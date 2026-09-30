import asyncio
import logging
import time
from typing import Annotated

import httpx
from fastapi import APIRouter, Depends, status
from fastapi.responses import JSONResponse

from app.book_assembly import (
    BookBodyTooLongError,
    BookDraftBlockError,
    BookDraftShapeError,
)
from app.book_prompt import BookPromptBuilder, BookRetryCorrection
from app.book_providers import BookGenerationUnsafeError, BookOutputContractError
from app.config import Settings
from app.dependencies import (
    BookProviderFactory,
    EmbeddingProviderFactory,
    ProviderFactory,
    get_app_settings,
    get_book_concurrency_gate,
    get_book_http_client,
    get_book_provider_factory,
    get_embedding_concurrency_gate,
    get_embedding_provider_factory,
    get_provider_concurrency_gate,
    get_provider_factory,
    get_safety_filter,
    get_sentence_http_client,
)
from app.embeddings import EmbeddingProvider, EmbeddingUnavailableError
from app.prompt import EvaluationPromptBuilder
from app.providers import (
    ProviderInvalidResponseError,
    ProviderTransientError,
)
from app.request_context import get_request_id
from app.safety import SafetyFilter, review_generated_blocks
from app.schemas.book import (
    BookContractViolationError,
    BookGenerationRequest,
    BookGenerationResponse,
    validate_against_request,
)
from app.schemas.errors import ErrorResponse
from app.schemas.evaluation import (
    EvaluationRequest,
    EvaluationResponse,
    validate_used_daily_words,
)


router = APIRouter(prefix="/internal/v1")
logger = logging.getLogger("ai_service.evaluation")
book_logger = logging.getLogger("ai_service.book_generation")

# 계약 위반 재시도(ISSUE-207). LLM 출력의 확률적 계약 위반은 재생성으로 대개
# 회복되므로, 총 예산 안에서 1회에 한해 다시 생성한다. 재시도 온도는 설정
# (book_llm_retry_temperature)으로 관리한다 — 본 시도 온도와 조정 방식을 맞춘다.
_BOOK_CONTRACT_MAX_ATTEMPTS = 2


def _retry_kind(exception: Exception) -> str:
    """위반 유형별 재시도 지시를 고른다.

    분량 초과와 초안 블록 규칙 위반은 JSON 형식 문제가 아니므로 전용 지시가 필요하다.
    형식 위반(초안 구조 오류·잘못된 JSON)은 schema로 모은다. 하위 타입을 먼저 본다.
    """
    if isinstance(exception, BookBodyTooLongError):
        return "length"
    if isinstance(exception, BookDraftBlockError):
        return "blocks"
    if isinstance(exception, BookDraftShapeError):
        return "schema"
    if isinstance(exception, BookContractViolationError):
        return "materials"
    return "schema"


def _invalid_book_response(
    correlation_id: str,
    job_id: int,
    attempt: int,
    started_at: float,
    exception: Exception,
    remaining: float | None = None,
) -> JSONResponse:
    """계약 위반 최종 실패의 로그·응답을 한 곳에서 만든다. 두 except 절이 공유한다."""
    book_logger.warning(
        "book_generation_invalid_response correlation_id=%s job_id=%s attempt=%d "
        "remaining_s=%s elapsed_ms=%d",
        correlation_id,
        job_id,
        attempt,
        f"{remaining:.1f}" if remaining is not None else "-",
        round((time.monotonic() - started_at) * 1000),
    )
    return JSONResponse(
        status_code=status.HTTP_502_BAD_GATEWAY,
        content=ErrorResponse(
            errorCode="INVALID_BOOK_RESPONSE",
            message=str(exception),
        ).model_dump(),
    )


async def _with_embedding(
    response: EvaluationResponse,
    request: EvaluationRequest,
    provider: EmbeddingProvider | None,
    correlation_id: str,
) -> EvaluationResponse:
    """평가 결과에 임베딩을 덧붙인다.

    임베딩은 유사도 검사용 부가 결과이므로, 미설정이거나 생성에 실패해도
    평가 응답 자체는 성공으로 유지한다. REJECTED 문장은 보스 제출 자격이
    없어 비교 대상이 아니므로 생성하지 않는다.
    """
    if provider is None or response.safety.status != "PASSED":
        return response

    try:
        result = await provider.embed(request.text)
    except EmbeddingUnavailableError as exception:
        logger.warning(
            "embedding_unavailable correlation_id=%s sentence_version_id=%s reason=%s",
            correlation_id,
            request.sentenceVersionId,
            exception,
        )
        return response
    except Exception:
        # 임베딩 어댑터가 놓친 예외(예: httpx.InvalidURL)까지 여기서 막는다.
        # 이 지점에서는 이미 과금·지연이 발생한 평가 결과를 손에 쥐고 있으므로
        # 어떤 이유로도 그것을 버리지 않는다. CancelledError는 BaseException이라
        # 여기에 걸리지 않으므로 취소 전파는 그대로 유지된다.
        logger.exception(
            "embedding_failed correlation_id=%s sentence_version_id=%s",
            correlation_id,
            request.sentenceVersionId,
        )
        return response

    return EvaluationResponse.model_validate(
        {
            **response.model_dump(),
            "embedding": result.vector,
            "embeddingModel": result.model,
            "embeddingDimension": result.dimension,
        }
    )


@router.post(
    "/evaluations",
    summary="문장 평가 요청",
    description=(
        "Spring Boot가 검증된 SentenceVersion Snapshot과 오늘의 단어 5개를 전송합니다. "
        "AI Service는 확정된 Request/Response 계약을 검증하고, "
        "현재 LLM provider가 없으면 503을 반환합니다."
    ),
    response_model=EvaluationResponse,
    status_code=status.HTTP_200_OK,
    response_description="Safety, 점수, 분위기, 사용 단어와 모델 버전을 포함한 평가 결과입니다.",
    responses={
        422: {
            "description": "Request JSON 형식 또는 필드 제약 조건을 만족하지 않습니다.",
        },
        502: {
            "model": ErrorResponse,
            "description": "LLM provider response did not satisfy the contract.",
        },
        503: {
            "model": ErrorResponse,
            "description": "아직 LLM provider가 설정되지 않았습니다.",
        },
    },
    tags=["evaluation"],
)
async def evaluate(
    request: EvaluationRequest,
    settings: Annotated[Settings, Depends(get_app_settings)],
    http_client: Annotated[httpx.AsyncClient, Depends(get_sentence_http_client)],
    concurrency_gate: Annotated[asyncio.Semaphore, Depends(get_provider_concurrency_gate)],
    embedding_concurrency_gate: Annotated[
        asyncio.Semaphore, Depends(get_embedding_concurrency_gate)
    ],
    provider_factory: Annotated[ProviderFactory, Depends(get_provider_factory)],
    embedding_provider_factory: Annotated[
        EmbeddingProviderFactory, Depends(get_embedding_provider_factory)
    ],
) -> EvaluationResponse | JSONResponse:
    """Evaluate one request through the configured provider adapter."""
    started_at = time.monotonic()
    correlation_id = str(request.correlationId)
    logger.info(
        "evaluation_started correlation_id=%s sentence_version_id=%s",
        correlation_id,
        request.sentenceVersionId,
        extra={
            "request_id": get_request_id(),
            "correlation_id": correlation_id,
            "sentence_version_id": request.sentenceVersionId,
        },
    )
    try:
        provider = provider_factory(
            settings,
            EvaluationPromptBuilder(),
            http_client,
            concurrency_gate,
        )
    except ProviderInvalidResponseError as exception:
        return JSONResponse(
            status_code=status.HTTP_502_BAD_GATEWAY,
            content=ErrorResponse(
                errorCode="UNSUPPORTED_LLM_PROVIDER",
                message=str(exception),
            ).model_dump(),
        )

    if provider is None:
        return JSONResponse(
            status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
            content=ErrorResponse(
                errorCode="LLM_NOT_CONFIGURED",
                message="LLM provider, API key, model, and base URL are not configured",
            ).model_dump(),
        )

    try:
        response = await provider.evaluate(request)
        validate_used_daily_words(request, response)
        response = await _with_embedding(
            response,
            request,
            embedding_provider_factory(settings, http_client, embedding_concurrency_gate),
            correlation_id,
        )
        logger.info(
            "evaluation_completed correlation_id=%s sentence_version_id=%s safety_status=%s "
            "embedded=%s elapsed_ms=%d",
            correlation_id,
            request.sentenceVersionId,
            response.safety.status,
            response.embedding is not None,
            round((time.monotonic() - started_at) * 1000),
            extra={
                "request_id": get_request_id(),
                "correlation_id": correlation_id,
                "sentence_version_id": request.sentenceVersionId,
                "safety_status": response.safety.status,
                "embedded": response.embedding is not None,
                "elapsed_ms": round((time.monotonic() - started_at) * 1000),
            },
        )
        return response
    except ProviderTransientError as exception:
        logger.warning(
            "evaluation_transient_failure correlation_id=%s sentence_version_id=%s elapsed_ms=%d",
            correlation_id,
            request.sentenceVersionId,
            round((time.monotonic() - started_at) * 1000),
            extra={
                "request_id": get_request_id(),
                "correlation_id": correlation_id,
                "sentence_version_id": request.sentenceVersionId,
                "elapsed_ms": round((time.monotonic() - started_at) * 1000),
            },
        )
        return JSONResponse(
            status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
            content=ErrorResponse(
                errorCode="LLM_PROVIDER_UNAVAILABLE",
                message=str(exception),
            ).model_dump(),
        )
    except (ProviderInvalidResponseError, ValueError) as exception:
        logger.warning(
            "evaluation_invalid_response correlation_id=%s sentence_version_id=%s elapsed_ms=%d",
            correlation_id,
            request.sentenceVersionId,
            round((time.monotonic() - started_at) * 1000),
            extra={
                "request_id": get_request_id(),
                "correlation_id": correlation_id,
                "sentence_version_id": request.sentenceVersionId,
                "elapsed_ms": round((time.monotonic() - started_at) * 1000),
            },
        )
        return JSONResponse(
            status_code=status.HTTP_502_BAD_GATEWAY,
            content=ErrorResponse(
                errorCode="INVALID_LLM_RESPONSE",
                message=str(exception),
            ).model_dump(),
        )


@router.post(
    "/book-generations",
    summary="공동 책 본문 생성 요청",
    description=(
        "Spring Boot가 보스런에 제출된 문장과 보스 테마를 전송하면, "
        "AI Service는 그 문장들을 하나의 이어지는 이야기로 엮어 본문 블록 배열로 반환합니다. "
        "각 블록은 순서, 원문 보존 등급(provenance), 본문, 원본 제출 식별자를 포함합니다. "
        "현재 LLM provider가 없으면 503을 반환합니다."
    ),
    response_model=BookGenerationResponse,
    status_code=status.HTTP_200_OK,
    response_description="순서대로 이어지는 본문 블록과 사용·제외된 제출 식별자입니다.",
    responses={
        422: {
            "description": "Request JSON 형식 또는 필드 제약 조건을 만족하지 않습니다.",
        },
        502: {
            "model": ErrorResponse,
            "description": (
                "LLM provider가 계약을 만족하지 않는 결과를 반환했거나(INVALID_BOOK_RESPONSE), "
                "생성 결과가 안전성 검수를 통과하지 못했습니다(BOOK_GENERATION_UNSAFE)."
            ),
        },
        503: {
            "model": ErrorResponse,
            "description": "아직 LLM provider가 설정되지 않았거나 호출에 실패했습니다.",
        },
    },
    tags=["book-generation"],
)
async def generate_book(
    request: BookGenerationRequest,
    settings: Annotated[Settings, Depends(get_app_settings)],
    http_client: Annotated[httpx.AsyncClient, Depends(get_book_http_client)],
    concurrency_gate: Annotated[asyncio.Semaphore, Depends(get_book_concurrency_gate)],
    provider_factory: Annotated[BookProviderFactory, Depends(get_book_provider_factory)],
    safety_filter: Annotated[SafetyFilter | None, Depends(get_safety_filter)],
) -> BookGenerationResponse | JSONResponse:
    """Generate one collaborative book body through the configured provider adapter."""
    started_at = time.monotonic()
    correlation_id = str(request.correlationId)
    book_logger.info(
        "book_generation_started correlation_id=%s job_id=%s boss_run_id=%s submissions=%d",
        correlation_id,
        request.jobId,
        request.bossRunId,
        len(request.submissions),
    )
    try:
        provider = provider_factory(
            settings,
            BookPromptBuilder(),
            http_client,
            concurrency_gate,
        )
    except ProviderInvalidResponseError as exception:
        return JSONResponse(
            status_code=status.HTTP_502_BAD_GATEWAY,
            content=ErrorResponse(
                errorCode="UNSUPPORTED_LLM_PROVIDER",
                message=str(exception),
            ).model_dump(),
        )

    if provider is None:
        return JSONResponse(
            status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
            content=ErrorResponse(
                errorCode="LLM_NOT_CONFIGURED",
                message="LLM provider, API key, model, and base URL are not configured",
            ).model_dump(),
        )

    # 계약 위반 재시도(ISSUE-207). 모든 시도는 하나의 예산(deadline) 안에서 수행돼
    # 재시도가 총 소요를 두 배로 늘리지 않는다. MQ 소비자도 이 함수를 그대로 호출하므로
    # HTTP와 MQ 경로의 재시도 정책이 구조적으로 같다.
    deadline = time.monotonic() + settings.book_request_budget_seconds
    attempt = 1
    correction: BookRetryCorrection | None = None
    while True:
        try:
            # 두 시도가 같은 절대 마감을 쓴다. 1차만 마감 없이 부르면 provider가 게이트
            # 획득 이후 예산을 새로 열어 두 가지가 깨진다 — 총 소요가 마감을 넘고(예산
            # 보장 붕괴), 반대로 게이트 대기가 길었던 요청은 1차 응답이 정상이어도 남은
            # 예산이 이미 음수라 재시도가 조용히 스킵된다(부하가 높을수록 재시도가 안
            # 도는 편향). 마감을 넘기면 provider가 게이트 획득 이후 남은 시간을 계산한다.
            response = await provider.generate(
                request,
                deadline=deadline,
                correction=correction,
                temperature=(
                    None if attempt == 1 else settings.book_llm_retry_temperature
                ),
            )
            # Spring도 저장 단계에서 USER_ORIGINAL 일치를 검증하고 어긋나면 결과 전체를 거절한다.
            # 여기서 먼저 걸러야 계약 위반과 안전성 위반을 구분된 오류로 돌려줄 수 있다.
            validate_against_request(request, response)
            break
        except BookGenerationUnsafeError as exception:
            # 안전성 차단은 재시도하지 않는다. 결과물 자체를 내보내면 안 되는 문제다.
            # 2차 시도에서 처음 차단되는 경우(온도·프롬프트가 달라졌으므로 가능)를
            # 로그만으로 구분할 수 있도록 다른 실패 로그와 같이 attempt를 남긴다.
            book_logger.warning(
                "book_generation_unsafe correlation_id=%s job_id=%s reason=provider_block "
                "attempt=%d elapsed_ms=%d",
                correlation_id,
                request.jobId,
                attempt,
                round((time.monotonic() - started_at) * 1000),
            )
            return JSONResponse(
                status_code=status.HTTP_502_BAD_GATEWAY,
                content=ErrorResponse(
                    errorCode="BOOK_GENERATION_UNSAFE",
                    message=str(exception),
                ).model_dump(),
            )
        except ProviderTransientError as exception:
            # 전송 재시도는 provider가 이미 소진했고, 예산 초과도 이 타입으로 온다.
            # 재시도 중(attempt≥2) 타임아웃이면 근본 원인은 직전 계약 위반이므로
            # last_violation으로 남겨 원인 추적이 503에서 끊기지 않게 한다.
            book_logger.warning(
                "book_generation_transient_failure correlation_id=%s job_id=%s attempt=%d "
                "elapsed_ms=%d last_violation=%s",
                correlation_id,
                request.jobId,
                attempt,
                round((time.monotonic() - started_at) * 1000),
                correction or "-",
            )
            return JSONResponse(
                status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
                content=ErrorResponse(
                    errorCode="LLM_PROVIDER_UNAVAILABLE",
                    message=str(exception),
                ).model_dump(),
            )
        except (BookOutputContractError, BookContractViolationError) as exception:
            # 정상 완료한 출력의 확률적 계약 위반만 재생성 대상이다.
            # 완주 가능할 때만 재시도한다. 하한이 1회 시도 시간(read timeout)보다
            # 짧으면 재시도가 거의 확실히 타임아웃 → 503으로 끝나, 재시도를 안 했다면
            # 502 INVALID_BOOK_RESPONSE였을 결과가 "나중에 다시 시도 가능"으로 뒤바뀐다.
            # 비싼 호출을 한 번 더 태우고 얻는 것이 잘못된 분류라면 손해다.
            remaining = deadline - time.monotonic()
            if (
                attempt >= _BOOK_CONTRACT_MAX_ATTEMPTS
                or remaining < settings.book_read_timeout_seconds
            ):
                return _invalid_book_response(
                    correlation_id, request.jobId, attempt, started_at, exception, remaining
                )
            book_logger.warning(
                "book_generation_contract_retry correlation_id=%s job_id=%s attempt=%d "
                "remaining_s=%.1f reason=%s",
                correlation_id,
                request.jobId,
                attempt,
                remaining,
                exception,
            )
            # 위반 유형마다 필요한 지시가 다르므로 함께 넘긴다. 사유 문구만 넘기면
            # 순번 위반에 "식별자를 지어내지 말라"가 나가는 식으로 어긋난다.
            correction = BookRetryCorrection(
                reason=str(exception), kind=_retry_kind(exception)
            )
            attempt += 1
        except ProviderInvalidResponseError as exception:
            # 재시도 대상이 아닌 위반: 4xx 설정 오류, MAX_TOKENS, 후보 부재 등은 같은
            # 요청을 반복해도 같은 결과다. 그 밖의 ValueError는 잡지 않는다 — 모델 잘못이
            # 아닌 코드 결함까지 INVALID_BOOK_RESPONSE로 위장되면 원인을 구분할 수 없다.
            return _invalid_book_response(
                correlation_id, request.jobId, attempt, started_at, exception
            )

    if safety_filter is not None:
        review = review_generated_blocks(response, safety_filter, request)
        if review.blocked:
            book_logger.warning(
                "book_generation_unsafe correlation_id=%s job_id=%s reason=content_review "
                "blocked_sequences=%s labels=%s elapsed_ms=%d",
                correlation_id,
                request.jobId,
                list(review.blocked_sequences),
                list(review.labels),
                round((time.monotonic() - started_at) * 1000),
            )
            return JSONResponse(
                status_code=status.HTTP_502_BAD_GATEWAY,
                content=ErrorResponse(
                    errorCode="BOOK_GENERATION_UNSAFE",
                    message="Generated book content did not pass the safety review.",
                ).model_dump(),
            )
        if review.flagged_labels:
            # 통과는 시키되 라벨을 남긴다. 어떤 계층이 얼마나 자주 flag로 걸리는지가
            # 필터 임계값을 실측으로 조정할 유일한 근거다. 라벨에는 표현 자체가 없다.
            book_logger.info(
                "book_generation_flagged correlation_id=%s job_id=%s labels=%s",
                correlation_id,
                request.jobId,
                list(review.flagged_labels),
            )

    book_logger.info(
        "book_generation_completed correlation_id=%s job_id=%s blocks=%d used=%d excluded=%d "
        "elapsed_ms=%d",
        correlation_id,
        request.jobId,
        len(response.contents),
        len(response.usedSubmissionIds),
        len(response.excludedSubmissionIds),
        round((time.monotonic() - started_at) * 1000),
    )
    return response
