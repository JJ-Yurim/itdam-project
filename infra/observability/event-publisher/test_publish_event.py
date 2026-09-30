import json
import threading
import unittest
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

import publish_event


class _CaptureHandler(BaseHTTPRequestHandler):
    payload = None

    def do_POST(self):
        length = int(self.headers["Content-Length"])
        type(self).payload = json.loads(self.rfile.read(length))
        self.send_response(204)
        self.end_headers()

    def log_message(self, _format, *args):
        return


class PublishEventTest(unittest.TestCase):
    def setUp(self):
        self.event = {
            "deploy_id": "develop-152-a1b2c3d4",
            "commit": "a1b2c3d4",
            "pipeline": "backend",
            "pipeline_stage": "Backend health check",
            "status": "FAILED",
            "jenkins_url": "https://jenkins.example/job/backend/152/console",
            "environment": "develop",
            "container": "backend-app",
            "exit_code": 1,
            "oom_killed": False,
            "exception_type": "java.lang.IllegalStateException",
            "source_file": "Application.java",
            "source_line": 42,
        }

    def test_builds_loki_payload_with_nanosecond_timestamp_and_typed_event(self):
        payload = publish_event.build_loki_payload(self.event, timestamp_ns=1234567890123456789)

        stream = payload["streams"][0]
        self.assertEqual(
            stream["stream"], {"service": "deployment", "environment": "develop"}
        )
        self.assertEqual(stream["values"][0][0], "1234567890123456789")
        self.assertEqual(json.loads(stream["values"][0][1]), self.event)

    def test_rejects_missing_required_fields_and_wrong_optional_types(self):
        for required in (
            "deploy_id",
            "commit",
            "pipeline",
            "pipeline_stage",
            "status",
            "jenkins_url",
        ):
            invalid = dict(self.event)
            invalid.pop(required)
            with self.subTest(required=required):
                with self.assertRaises(ValueError):
                    publish_event.validate_event(invalid)

        for field, invalid_value in (
            ("exit_code", "1"),
            ("oom_killed", "false"),
            ("source_line", "42"),
            ("container", 7),
        ):
            invalid = dict(self.event, **{field: invalid_value})
            with self.subTest(field=field):
                with self.assertRaises(ValueError):
                    publish_event.validate_event(invalid)

    def test_rejects_sensitive_field_names_at_any_depth(self):
        for forbidden in ("password", "api_token", "Authorization", "sessionCookie", "secret_key"):
            invalid = dict(self.event)
            invalid["metadata"] = {forbidden: "must-not-be-sent"}
            with self.subTest(forbidden=forbidden):
                with self.assertRaises(ValueError):
                    publish_event.validate_event(invalid)

    def test_posts_one_event_to_the_configured_loki_endpoint(self):
        server = ThreadingHTTPServer(("127.0.0.1", 0), _CaptureHandler)
        thread = threading.Thread(target=server.serve_forever, daemon=True)
        thread.start()
        try:
            publish_event.publish(
                self.event,
                f"http://127.0.0.1:{server.server_port}/loki/api/v1/push",
            )
        finally:
            server.shutdown()
            server.server_close()
            thread.join(timeout=2)

        captured_event = json.loads(_CaptureHandler.payload["streams"][0]["values"][0][1])
        self.assertEqual(captured_event["deploy_id"], self.event["deploy_id"])


class SummarizeTest(unittest.TestCase):
    def test_extracts_last_java_cause_and_its_first_application_frame(self):
        raw = """\
java.lang.RuntimeException: wrapper
    at com.ssafy.c206.backend.Start.main(Start.java:10)
Caused by: java.lang.IllegalArgumentException: middle
    at com.ssafy.c206.backend.Middle.call(Middle.java:20)
Caused by: org.flywaydb.core.api.exception.FlywayValidateException: deepest
    at org.flywaydb.core.Flyway.run(Flyway.java:90)
    at com.ssafy.c206.backend.config.DatabaseConfig.migrate(DatabaseConfig.java:67)
"""

        self.assertEqual(
            publish_event.summarize("backend", raw),
            {
                "exception_type": "org.flywaydb.core.api.exception.FlywayValidateException",
                "source_file": "DatabaseConfig.java",
                "source_line": 67,
            },
        )

    def test_extracts_final_python_exception_and_final_application_frame(self):
        raw = """\
Traceback (most recent call last):
  File "/usr/local/lib/python3.12/site-packages/fastapi/routing.py", line 1, in app
    result = await endpoint()
  File "/app/app/api/routes.py", line 82, in evaluate
    await provider.evaluate()
  File "/app/app/providers.py", line 211, in evaluate
    raise ProviderError()
app.providers.ProviderError: provider failed
"""

        self.assertEqual(
            publish_event.summarize("ai-service", raw),
            {
                "exception_type": "app.providers.ProviderError",
                "source_file": "providers.py",
                "source_line": 211,
            },
        )

    def test_returns_null_location_for_malformed_logs(self):
        self.assertEqual(
            publish_event.summarize("backend", "container stopped without a stack"),
            {"exception_type": None, "source_file": None, "source_line": None},
        )


if __name__ == "__main__":
    unittest.main()
