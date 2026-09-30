import importlib
import io
import json
import logging
import sys
from uuid import UUID, uuid4

import pytest
from fastapi.testclient import TestClient

from app.application import create_app
from app.config import Settings
from app.logging_config import JsonLogFormatter
from app.request_context import get_request_id, reset_request_id, set_request_id


def settings() -> Settings:
    return Settings(
        _env_file=None,
        app_env="test",
        deploy_commit="test-commit",
        llm_api_key="secret-test-key",
    )


@pytest.fixture
def _restore_logging():
    """configure_logging은 root와 uvicorn 로거의 핸들러를 비운다.

    프로세스 전역 상태라 복구하지 않으면 뒤따르는 테스트의 caplog 수집이
    실행 순서에 따라 깨진다.
    """
    names = (None, "uvicorn", "uvicorn.error", "uvicorn.access", "ai_service")
    saved = {
        name: (
            logging.getLogger(name).level,
            logging.getLogger(name).handlers[:],
            logging.getLogger(name).propagate,
        )
        for name in names
    }
    yield
    for name, (level, handlers, propagate) in saved.items():
        logger = logging.getLogger(name)
        logger.setLevel(level)
        logger.handlers = handlers
        logger.propagate = propagate


def test_production_entrypoint_emits_each_record_once(capsys, _restore_logging):
    """운영 진입점에서 같은 레코드가 두 번 나가지 않아야 한다.

    create_app은 ai_service 로거에 평문 폴백 핸들러를 붙이고 그 로거는
    propagate가 살아 있다. configure_logging이 그 핸들러를 떼어내지 않거나
    create_app보다 먼저 호출되면, 레코드 하나가 평문 한 줄과 JSON 한 줄로
    출력된다. 로그량이 두 배가 되고 절반이 JSON이 아니게 되어 구조화 로그를
    전제한 Grafana 질의가 절반을 놓친다.

    반드시 main.py를 임포트해 검증한다. 테스트 본문에서 create_app과
    configure_logging을 직접 호출하면 진입점의 호출 순서가 잘못되어도 이
    테스트가 통과해 버려서, 정작 막아야 할 회귀를 놓친다.
    """
    sys.modules.pop("main", None)
    importlib.import_module("main")
    # 기동 과정에서 나온 로그는 이 검증 대상이 아니다.
    capsys.readouterr()

    logging.getLogger("ai_service.evaluation").info("evaluation_started")

    lines = [line for line in capsys.readouterr().out.splitlines() if line.strip()]

    assert len(lines) == 1, f"레코드 하나가 {len(lines)}줄로 출력됨: {lines}"
    assert json.loads(lines[0])["message"] == "evaluation_started"


def test_request_middleware_preserves_valid_header_and_cleans_context():
    application = create_app(settings())

    @application.get("/_test/request-id")
    async def request_id_probe():
        return {"requestId": get_request_id()}

    supplied = str(uuid4())
    with TestClient(application) as client:
        response = client.get("/_test/request-id", headers={"X-Request-ID": supplied})

    assert response.status_code == 200
    assert response.headers["X-Request-ID"] == supplied
    assert response.json()["requestId"] == supplied
    assert get_request_id() is None


def test_request_middleware_replaces_missing_and_malformed_headers():
    application = create_app(settings())

    @application.get("/_test/request-id")
    async def request_id_probe():
        return {"requestId": get_request_id()}

    with TestClient(application) as client:
        missing = client.get("/_test/request-id")
        malformed = client.get(
            "/_test/request-id", headers={"X-Request-ID": "not-a-uuid"}
        )

    for response in (missing, malformed):
        generated = response.headers["X-Request-ID"]
        assert str(UUID(generated)) == generated
        assert response.json()["requestId"] == generated
    assert malformed.headers["X-Request-ID"] != "not-a-uuid"
    assert get_request_id() is None


def test_request_context_is_cleaned_when_downstream_raises():
    application = create_app(settings())

    @application.get("/_test/failure")
    async def failure_probe():
        assert get_request_id() is not None
        raise RuntimeError("test failure")

    with TestClient(application, raise_server_exceptions=False) as client:
        response = client.get("/_test/failure")

    assert response.status_code == 500
    assert get_request_id() is None


def test_json_formatter_includes_context_without_settings_or_sentence_text():
    output = io.StringIO()
    handler = logging.StreamHandler(output)
    handler.setFormatter(JsonLogFormatter(settings()))
    logger = logging.getLogger("structured-test")
    logger.handlers = [handler]
    logger.propagate = False
    logger.setLevel(logging.INFO)
    request_id = str(uuid4())
    token = set_request_id(request_id)
    try:
        logger.info(
            "evaluation_started",
            extra={
                "request_id": request_id,
                "correlation_id": str(uuid4()),
                "sentence_version_id": 42,
            },
        )
    finally:
        reset_request_id(token)

    payload = json.loads(output.getvalue())
    assert payload["service"] == "ai-service"
    assert payload["environment"] == "test"
    assert payload["commit"] == "test-commit"
    assert payload["request_id"] == request_id
    assert payload["message"] == "evaluation_started"
    assert payload["sentence_version_id"] == 42
    assert "private sentence marker" not in output.getvalue()
    assert "secret-test-key" not in output.getvalue()


def test_json_formatter_includes_deepest_exception_source_and_stack():
    output = io.StringIO()
    handler = logging.StreamHandler(output)
    handler.setFormatter(JsonLogFormatter(settings()))
    logger = logging.getLogger("structured-exception-test")
    logger.handlers = [handler]
    logger.propagate = False
    logger.setLevel(logging.ERROR)

    try:
        exec(compile("raise ValueError('diagnostic')", "/app/app/failure.py", "exec"))
    except ValueError:
        logger.exception("request_failed")

    payload = json.loads(output.getvalue())
    assert payload["exception_type"] == "ValueError"
    assert payload["source_file"] == "failure.py"
    assert payload["source_line"] == 1
    assert "ValueError: diagnostic" in payload["stack_trace"]
