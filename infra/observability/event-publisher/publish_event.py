import argparse
import json
import re
import sys
import time
import urllib.request


LOKI_PUSH_URL = "http://loki:3100/loki/api/v1/push"
REQUIRED_STRING_FIELDS = (
    "deploy_id",
    "commit",
    "pipeline",
    "pipeline_stage",
    "status",
    "jenkins_url",
)
OPTIONAL_FIELDS = {
    "timestamp": (str,),
    "level": (str,),
    "service": (str,),
    "environment": (str,),
    "container": (str,),
    "exit_code": (int,),
    "oom_killed": (bool,),
    "exception_type": (str, type(None)),
    "source_file": (str, type(None)),
    "source_line": (int, type(None)),
}
FORBIDDEN_KEY_PARTS = ("password", "token", "authorization", "cookie", "secret")


def validate_event(event: object) -> dict[str, object]:
    if not isinstance(event, dict):
        raise ValueError("event must be a JSON object")
    _reject_sensitive_keys(event)

    for field in REQUIRED_STRING_FIELDS:
        value = event.get(field)
        if not isinstance(value, str) or not value.strip():
            raise ValueError(f"{field} must be a non-empty string")

    known_fields = set(REQUIRED_STRING_FIELDS) | set(OPTIONAL_FIELDS)
    unknown_fields = set(event) - known_fields
    if unknown_fields:
        raise ValueError(f"unknown event fields: {sorted(unknown_fields)}")

    for field, allowed_types in OPTIONAL_FIELDS.items():
        if field not in event:
            continue
        value = event[field]
        if field in {"exit_code", "source_line"} and isinstance(value, bool):
            raise ValueError(f"{field} has an invalid type")
        if not isinstance(value, allowed_types):
            raise ValueError(f"{field} has an invalid type")

    return event


def build_loki_payload(
    event: dict[str, object], timestamp_ns: int | None = None
) -> dict[str, object]:
    validated = validate_event(event)
    environment = validated.get("environment", "unknown")
    timestamp = str(time.time_ns() if timestamp_ns is None else timestamp_ns)
    return {
        "streams": [
            {
                "stream": {"service": "deployment", "environment": environment},
                "values": [[timestamp, json.dumps(validated, ensure_ascii=False)]],
            }
        ]
    }


def publish(event: dict[str, object], loki_url: str = LOKI_PUSH_URL) -> None:
    body = json.dumps(build_loki_payload(event), ensure_ascii=False).encode("utf-8")
    request = urllib.request.Request(
        loki_url,
        data=body,
        headers={"Content-Type": "application/json"},
        method="POST",
    )
    with urllib.request.urlopen(request, timeout=5):
        pass


def summarize(service: str, raw_logs: str) -> dict[str, object]:
    if service == "ai-service":
        return _summarize_python(raw_logs)
    return _summarize_java(raw_logs)


def _summarize_java(raw_logs: str) -> dict[str, object]:
    causes = list(
        re.finditer(r"(?m)^Caused by:\s+([A-Za-z_$][\w.$]+)(?::|$)", raw_logs)
    )
    exception_type = None
    section = raw_logs
    if causes:
        deepest = causes[-1]
        exception_type = deepest.group(1)
        section = raw_logs[deepest.end() :]
    else:
        top_level = re.search(
            r"(?m)^([A-Za-z_$][\w.$]+(?:Exception|Error))(?::|$)", raw_logs
        )
        if top_level:
            exception_type = top_level.group(1)

    frame = re.search(
        r"(?m)^\s*at\s+com\.ssafy\.c206\.[^(]+\(([^():]+\.java):(\d+)\)",
        section,
    )
    return {
        "exception_type": exception_type,
        "source_file": frame.group(1) if frame else None,
        "source_line": int(frame.group(2)) if frame else None,
    }


def _summarize_python(raw_logs: str) -> dict[str, object]:
    exceptions = list(
        re.finditer(
            r"(?m)^([A-Za-z_][\w.]*(?:Error|Exception|Failure))(?::|$)", raw_logs
        )
    )
    frames = list(
        re.finditer(r'File\s+"/app/app/([^"]+)",\s+line\s+(\d+)', raw_logs)
    )
    final_frame = frames[-1] if frames else None
    return {
        "exception_type": exceptions[-1].group(1) if exceptions else None,
        "source_file": final_frame.group(1).rsplit("/", 1)[-1] if final_frame else None,
        "source_line": int(final_frame.group(2)) if final_frame else None,
    }


def _reject_sensitive_keys(value: object) -> None:
    if isinstance(value, dict):
        for key, nested in value.items():
            normalized = str(key).lower()
            if any(part in normalized for part in FORBIDDEN_KEY_PARTS):
                raise ValueError(f"sensitive field name is forbidden: {key}")
            _reject_sensitive_keys(nested)
    elif isinstance(value, list):
        for nested in value:
            _reject_sensitive_keys(nested)


def main() -> int:
    parser = argparse.ArgumentParser()
    commands = parser.add_subparsers(dest="command", required=True)
    publish_parser = commands.add_parser("publish")
    publish_parser.add_argument("--stdin", action="store_true", required=True)
    summarize_parser = commands.add_parser("summarize")
    summarize_parser.add_argument("--service", required=True)
    args = parser.parse_args()

    if args.command == "publish":
        publish(json.load(sys.stdin))
    else:
        json.dump(summarize(args.service, sys.stdin.read()), sys.stdout)
        sys.stdout.write("\n")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
