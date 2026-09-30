import json
import logging
import sys
import traceback
from datetime import datetime, timezone
from pathlib import PurePosixPath
from types import TracebackType

from app.config import Settings
from app.request_context import get_request_id


# create_app이 ai_service 로거에 붙이는 폴백 핸들러의 이름이다. 여기에 두어야
# configure_logging이 그 핸들러만 골라 떼어낼 수 있다(아래 설명 참고).
SERVICE_FALLBACK_HANDLER_NAME = "ai_service_stdout"

_STRUCTURED_FIELDS = (
    "correlation_id",
    "sentence_version_id",
    "job_id",
    "boss_run_id",
    "safety_status",
    "embedded",
    "elapsed_ms",
)


class JsonLogFormatter(logging.Formatter):
    def __init__(self, settings: Settings):
        super().__init__()
        self.environment = settings.app_env
        self.commit = settings.deploy_commit

    def format(self, record: logging.LogRecord) -> str:
        payload: dict[str, object] = {
            "timestamp": datetime.fromtimestamp(
                record.created, tz=timezone.utc
            ).isoformat(timespec="milliseconds").replace("+00:00", "Z"),
            "level": record.levelname,
            "logger": record.name,
            "message": record.getMessage(),
            "service": "ai-service",
            "environment": self.environment,
            "commit": self.commit,
            "request_id": getattr(record, "request_id", None) or get_request_id(),
        }

        for field in _STRUCTURED_FIELDS:
            if hasattr(record, field):
                payload[field] = getattr(record, field)

        if record.exc_info is not None:
            exception = _deepest_exception(record.exc_info[1])
            source_file, source_line = _application_source(exception.__traceback__)
            payload.update(
                exception_type=type(exception).__name__,
                source_file=source_file,
                source_line=source_line,
                stack_trace="".join(traceback.format_exception(*record.exc_info)),
            )

        return json.dumps(payload, ensure_ascii=False, separators=(",", ":"))


def configure_logging(settings: Settings) -> None:
    """운영 진입점(main.py)에서 모든 로그를 JSON 한 줄로 통일한다.

    create_app 뒤에 호출해야 한다. create_app은 ai_service 로거에 평문 폴백
    핸들러를 붙이는데(root에 핸들러가 없는 로컬·테스트에서 INFO가 묻히는 것을
    막는 장치다), ai_service는 propagate가 살아 있어 그 핸들러를 남겨 두면
    같은 레코드가 평문 한 줄과 JSON 한 줄로 두 번 나간다. 로그량이 두 배가 되고
    절반이 JSON이 아니게 되어 구조화 로깅 전제가 깨진다.

    폴백 핸들러만 이름으로 골라 떼어내고 레벨과 propagate는 건드리지 않는다.
    레벨은 LOG_LEVEL에서 온 값이라 INFO로 덮으면 설정이 무시되고, propagate를
    끄면 caplog로 ai_service 로그를 수집하는 기존 테스트가 깨진다.
    """
    formatter = JsonLogFormatter(settings)
    handler = logging.StreamHandler(sys.stdout)
    handler.setFormatter(formatter)

    for logger_name in (None, "uvicorn", "uvicorn.error", "uvicorn.access"):
        logger = logging.getLogger(logger_name)
        logger.handlers.clear()
        logger.addHandler(handler)
        logger.setLevel(logging.INFO)
        logger.propagate = False

    service_logger = logging.getLogger("ai_service")
    for existing in [
        h
        for h in service_logger.handlers
        if h.get_name() == SERVICE_FALLBACK_HANDLER_NAME
    ]:
        service_logger.removeHandler(existing)


def _deepest_exception(exception: BaseException) -> BaseException:
    current = exception
    visited: set[int] = set()
    while id(current) not in visited:
        visited.add(id(current))
        cause = current.__cause__ or current.__context__
        if cause is None or id(cause) in visited:
            break
        current = cause
    return current


def _application_source(
    traceback_value: TracebackType | None,
) -> tuple[str | None, int | None]:
    selected: tuple[str, int] | None = None
    current = traceback_value
    while current is not None:
        filename = current.tb_frame.f_code.co_filename.replace("\\", "/")
        if filename.startswith("/app/app/"):
            selected = (PurePosixPath(filename).name, current.tb_lineno)
        current = current.tb_next
    return selected if selected is not None else (None, None)
