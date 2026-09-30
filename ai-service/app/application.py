import asyncio
import logging
import sys
from contextlib import AsyncExitStack, asynccontextmanager

from fastapi import FastAPI
from fastapi.responses import JSONResponse
from pydantic import BaseModel, ConfigDict, Field

from app.api.routes import router
from app.book_mq import BookGenerationMqConsumer
from app.book_providers import build_book_provider
from app.config import Settings, get_settings
from app.embeddings import build_embedding_provider
from app.evaluation_mq import SentenceEvaluationMqConsumer
from app.http_clients import build_book_http_client, build_sentence_http_client
from app.providers import build_provider
from app.logging_config import SERVICE_FALLBACK_HANDLER_NAME
from app.middleware import RequestContextMiddleware
from app.safety import SafetyFilter


class HealthResponse(BaseModel):
    """서비스 기동 상태와 LLM 설정 상태를 반환합니다."""

    model_config = ConfigDict(extra="forbid")

    status: str = Field(description="서비스 상태입니다. 정상 기동 시 UP입니다.", examples=["UP"])
    service: str = Field(description="서비스 이름입니다.", examples=["mvp-ai-service"])
    environment: str = Field(description="현재 실행 프로필 또는 환경입니다.", examples=["local"])
    llmConfigured: bool = Field(
        description="LLM provider, API key, model 설정이 모두 존재하는지 여부입니다.",
        examples=[False],
    )
    embeddingConfigured: bool = Field(
        description=(
            "임베딩 모델, API key, base URL 설정이 모두 존재하는지 여부입니다. "
            "false이면 평가는 정상 동작하되 응답에 임베딩이 포함되지 않습니다."
        ),
        examples=[False],
    )


class ReadinessResponse(BaseModel):
    model_config = ConfigDict(extra="forbid")

    status: str
    service: str
    llmConfigured: bool


def _lifespan(settings: Settings):
    @asynccontextmanager
    async def lifespan(application: FastAPI):
        async with AsyncExitStack() as stack:
            application.state.sentence_http_client = await stack.enter_async_context(
                build_sentence_http_client(settings)
            )
            application.state.book_http_client = await stack.enter_async_context(
                build_book_http_client(settings)
            )
            application.state.provider_concurrency_gate = asyncio.Semaphore(
                settings.llm_max_concurrency
            )
            application.state.embedding_concurrency_gate = asyncio.Semaphore(
                settings.embedding_max_concurrency
            )
            # 책 생성은 한 건이 수 분 동안 외부 연결을 붙잡으므로 문장 처리와 풀·게이트를 모두 나눈다.
            application.state.book_concurrency_gate = asyncio.Semaphore(
                settings.book_max_concurrency
            )
            if settings.book_mq_enabled:
                mq_consumer = BookGenerationMqConsumer(application)
                stack.push_async_callback(mq_consumer.close)
                await mq_consumer.start()
                application.state.book_mq_consumer = mq_consumer
            if settings.evaluation_mq_enabled:
                evaluation_consumer = SentenceEvaluationMqConsumer(application)
                stack.push_async_callback(evaluation_consumer.close)
                await evaluation_consumer.start()
                application.state.evaluation_mq_consumer = evaluation_consumer
            yield

    return lifespan


_LOG_LEVELS = frozenset({"DEBUG", "INFO", "WARNING", "ERROR", "CRITICAL"})
_HANDLER_NAME = SERVICE_FALLBACK_HANDLER_NAME


def _configure_logging(level: str) -> None:
    """애플리케이션 로거에 폴백 핸들러를 직접 붙인다.

    운영 진입점(main.py)은 create_app 뒤에 configure_logging을 호출해 이 핸들러를
    떼어내고 JSON 핸들러로 대체한다. 여기서 붙이는 평문 핸들러는 root에 핸들러가
    없는 로컬 실행과 테스트에서만 실제로 출력을 담당한다.

    ai_service.* 로거는 uvicorn이 설정하지 않으므로 루트로 전파되는데, 루트 기본
    핸들러는 WARNING 이상만 출력한다. 그래서 평가·책 생성의 성공 경로(INFO)가 통째로
    묻히고 경고만 보였다. 운영에서 "도는지 안 도는지"를 확인하려면 INFO가 필요하다.
    uvicorn 자체 로거 설정과 충돌하지 않도록 루트를 건드리지 않고 이 계열만 잡는다.

    알 수 없는 레벨은 INFO로 폴백한다. setLevel은 인식할 수 없는 문자열에 ValueError를
    던지는데, 로그 레벨 오타로 서비스가 기동조차 못 하는 것은 과한 실패다.
    """
    handler = logging.StreamHandler(sys.stdout)
    handler.setFormatter(
        logging.Formatter("%(asctime)s %(levelname)s %(name)s %(message)s")
    )
    # 이 함수가 붙인 핸들러에만 이름을 달아, 재호출 시 그것만 걷어낸다. handlers를
    # 통째로 갈아치우면 외부에서 붙인 파일·APM 핸들러까지 조용히 떼어낸다.
    handler.set_name(_HANDLER_NAME)
    service_logger = logging.getLogger("ai_service")
    normalized = (level or "").strip().upper()
    if normalized not in _LOG_LEVELS:
        normalized = "INFO"
    service_logger.setLevel(normalized)
    for existing in [
        h for h in service_logger.handlers if h.get_name() == _HANDLER_NAME
    ]:
        service_logger.removeHandler(existing)
    service_logger.addHandler(handler)
    # 전파는 끄지 않는다. 루트에는 핸들러가 없어 중복 출력이 생기지 않고,
    # 테스트의 caplog는 루트에 붙는 캡처 핸들러로 로그를 수집한다.
    if normalized != (level or "").strip().upper():
        service_logger.warning(
            "log_level_fallback requested=%s applied=%s", level, normalized
        )


def create_app(settings: Settings | None = None) -> FastAPI:
    """Create an application whose settings and providers can be isolated in tests."""
    app_settings = settings or get_settings()
    _configure_logging(app_settings.log_level)
    application = FastAPI(
        title=app_settings.app_name,
        version="mvp-runtime-v1",
        description=(
            "MVP AI Service의 내부 HTTP API입니다. "
            "문장 평가 요청은 Spring Boot에서만 호출합니다."
        ),
        lifespan=_lifespan(app_settings),
        openapi_tags=[
            {
                "name": "system",
                "description": "AI Service 기동 상태와 환경 확인 API입니다.",
            },
            {
                "name": "evaluation",
                "description": "Spring Boot와 AI Service 사이의 문장 평가 계약 API입니다.",
            },
            {
                "name": "book-generation",
                "description": "Spring Boot와 AI Service 사이의 공동 책 본문 생성 계약 API입니다.",
            },
        ],
    )
    application.state.settings = app_settings
    application.state.book_provider_factory = build_book_provider
    # 평가 MQ 소비자가 HTTP 의존성 주입 대신 여기서 팩토리를 꺼낸다. 테스트는 이
    # state를 갈아끼워 실제 I/O 없이 소비자 경로를 검증한다(book_provider_factory와 동일).
    application.state.provider_factory = build_provider
    application.state.embedding_provider_factory = build_embedding_provider
    application.add_middleware(RequestContextMiddleware)
    # 규칙 사전을 앱 생성 시점에 읽는다. 첫 책 생성 요청까지 미루면 사전 누락·JSON 파손이
    # 배포 검증을 통과하고 런타임 500으로 드러난다. 여기서 실패하면 기동 자체가 실패한다.
    application.state.safety_filter = (
        SafetyFilter() if app_settings.safety_filter_enabled else None
    )
    application.include_router(router)

    @application.get("/health", response_model=HealthResponse, tags=["system"])
    async def health() -> HealthResponse:
        return HealthResponse(
            status="UP",
            service=app_settings.app_name,
            environment=app_settings.app_env,
            llmConfigured=app_settings.llm_configured,
            embeddingConfigured=app_settings.embedding_configured,
        )

    @application.get(
        "/health/ready",
        response_model=ReadinessResponse,
        responses={503: {"model": ReadinessResponse}},
        tags=["system"],
    )
    async def readiness() -> ReadinessResponse | JSONResponse:
        ready = app_settings.llm_configured and app_settings.llm_provider_supported
        response = ReadinessResponse(
            status="READY" if ready else "NOT_READY",
            service=app_settings.app_name,
            llmConfigured=app_settings.llm_configured,
        )
        if not ready:
            return JSONResponse(status_code=503, content=response.model_dump())
        return response

    return application
