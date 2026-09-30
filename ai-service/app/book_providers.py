"""공동 책 본문 생성 Provider.

전송 계층(재시도·백오프·동시성)은 평가와 함께 :class:`GeminiGmsCaller`에서 공유하고,
여기서는 책 생성에만 해당하는 것을 다룬다.

  - 평가와 다른 모델 파라미터: 창작이므로 온도가 높고 출력 한도가 훨씬 크다.
  - 요청 단위 read timeout 명시: 직접 생성된 Client를 주입해도 책 생성 예산을 유지한다.
  - 잘린 envelope 재시도: 본문이 길어 GMS 프록시의 절단에 실제로 노출된다.
  - 집계는 모델에게 시키지 않고 코드가 센다: 모델이 스스로 적은 사용 문장 수가 실제와
    어긋난 사례가 있었다(60개라고 적고 39개만 사용).
"""

from __future__ import annotations

import asyncio
import json
import random
import time
from collections.abc import Awaitable, Callable
from typing import Protocol

import httpx

from app.book_assembly import build_book_response, parse_draft
from app.book_prompt import BookPromptBuilder, BookRetryCorrection
from app.config import Settings
from app.providers import (
    GeminiGmsCaller,
    ProviderInvalidResponseError,
    ProviderTransientError,
    _normalize_json_content,
)
from app.schemas.book import (
    MAX_CONTENT_LENGTH,
    MAX_SUMMARY_LENGTH,
    BookContractViolationError,
    BookGenerationRequest,
    BookGenerationResponse,
)


_BOOK_DRAFT_JSON_SCHEMA = {
    "type": "object",
    "additionalProperties": False,
    "properties": {
        "contents": {
            "type": "array",
            "minItems": 1,
            "items": {
                "type": "object",
                "additionalProperties": False,
                "properties": {
                    "sequenceNo": {"type": "integer", "minimum": 1},
                    # 상한을 스키마에 실어 모델이 애초에 초과 문장을 만들지 않게 한다.
                    # 여기 없으면 초과가 응답 파싱 단계에서야 늦게 드러난다.
                    "text": {
                        "type": "string",
                        "minLength": 1,
                        "maxLength": MAX_CONTENT_LENGTH,
                    },
                    # minimum을 실어 0·음수가 애초에 나오지 않게 한다. 없으면 초안
                    # 검증에서야 잡히고 재시도 지시도 식별자 얘기가 아닌 쪽으로 간다.
                    "sourceSubmissionId": {"type": ["integer", "null"], "minimum": 1},
                    "paragraphBreakBefore": {"type": "boolean"},
                },
                "required": [
                    "sequenceNo",
                    "text",
                    "sourceSubmissionId",
                    "paragraphBreakBefore",
                ],
            },
        },
        "summary": {
            "type": "string",
            "minLength": 1,
            "maxLength": MAX_SUMMARY_LENGTH,
        },
    },
    # required에서 빠지면 모델이 필드를 통째로 생략해도 스키마 강제를 통과한다.
    # body·contentType·집계는 모델에게 요구하지 않는다(ISSUE-212) — 서버가 계산한다.
    "required": ["contents", "summary"],
}


class BookGenerationUnsafeError(RuntimeError):
    """생성 결과가 안전성 검수를 통과하지 못했다 (정책 §12.4).

    계약 위반과 반드시 구분한다. 계약 위반은 프롬프트나 파서를 고쳐야 하는 문제지만
    이쪽은 결과물 자체를 내보내면 안 되는 문제다.
    """


class BookOutputContractError(ProviderInvalidResponseError):
    """모델이 정상 완료한 출력이 책 생성 계약을 어겼다(ISSUE-207).

    부모(ProviderInvalidResponseError)는 4xx 설정 오류·MAX_TOKENS처럼 재시도해도
    나아지지 않는 실패까지 포괄한다. 이 하위 타입은 "재생성하면 대개 통과하는"
    확률적 출력 위반(잘못된 JSON·스키마·교차 필드 계약)만 표시해, 호출부가 제한
    재시도 대상을 구분할 수 있게 한다.
    """


class BookProvider(Protocol):
    async def generate(
        self,
        request: BookGenerationRequest,
        *,
        deadline: float | None = None,
        correction: BookRetryCorrection | None = None,
        temperature: float | None = None,
    ) -> BookGenerationResponse:
        ...


class GeminiGmsBookProvider(GeminiGmsCaller):
    """GMS Gemini generateContent로 공동 책 본문을 생성한다."""

    def __init__(
        self,
        settings: Settings,
        prompt_builder: BookPromptBuilder,
        http_client: httpx.AsyncClient,
        concurrency_gate: asyncio.Semaphore,
        sleep: Callable[[float], Awaitable[None]] = asyncio.sleep,
        jitter: Callable[[float, float], float] = random.uniform,
    ):
        super().__init__(settings, http_client, concurrency_gate, sleep, jitter)
        self._prompt_builder = prompt_builder

    async def generate(
        self,
        request: BookGenerationRequest,
        *,
        deadline: float | None = None,
        correction: BookRetryCorrection | None = None,
        temperature: float | None = None,
    ) -> BookGenerationResponse:
        """계약 위반 재시도(ISSUE-207)용 선택 인자를 받는다.

        deadline은 time.monotonic() 기준 절대 마감 시각이다. 여러 시도를 하나의 총
        예산에 묶는 호출부가 전달하며, 없으면 기존처럼 설정 예산을 그대로 쓴다.
        correction·temperature는 재시도에서 직전 위반 사유를 프롬프트에 싣고
        형식 안정을 위해 온도를 낮추는 데 쓴다.
        """
        # 슬롯 대기는 기본 예산에 포함하지 않는다. 대기 중 예산을 다 써 실제 호출을
        # 한 번도 못 하는 일을 막고, 취소 시 Semaphore 획득도 함께 취소되게 한다.
        # 단 deadline이 오면 남은 시간을 게이트 획득 '이후'에 계산한다 — 상대 시간으로
        # 받으면 슬롯 대기만큼 마감이 뒤로 밀려 총 소요가 예산을 넘는다.
        async with self._concurrency_gate:
            if deadline is not None:
                timeout = deadline - time.monotonic()
                if timeout <= 0:
                    raise ProviderTransientError(
                        "Book generation request budget was exceeded."
                    )
            else:
                timeout = self._settings.book_request_budget_seconds
            try:
                return await asyncio.wait_for(
                    self._generate_with_budget(request, correction, temperature),
                    timeout=timeout,
                )
            except asyncio.TimeoutError as exc:
                raise ProviderTransientError(
                    "Book generation request budget was exceeded."
                ) from exc

    async def _generate_with_budget(
        self,
        request: BookGenerationRequest,
        correction: BookRetryCorrection | None = None,
        temperature: float | None = None,
    ) -> BookGenerationResponse:
        endpoint, payload, headers = self._build_request(request, correction, temperature)
        body = await self._post_with_retries(
            endpoint,
            payload,
            headers,
            request.correlationId,
            read_timeout=self._read_timeout(),
            retry_truncated_envelope=True,
        )
        return self._parse_body(body, request)

    def _read_timeout(self) -> httpx.Timeout:
        return httpx.Timeout(
            connect=self._settings.llm_connect_timeout_seconds,
            read=self._settings.book_read_timeout_seconds,
            write=self._settings.llm_write_timeout_seconds,
            pool=self._settings.llm_pool_timeout_seconds,
        )

    def _build_request(
        self,
        request: BookGenerationRequest,
        correction: BookRetryCorrection | None = None,
        temperature: float | None = None,
    ) -> tuple[str, dict, dict[str, str]]:
        system_prompt, user_prompt = self._prompt_builder.build(request, correction)
        endpoint = self._generate_content_endpoint(self._settings.book_model)
        payload = {
            "systemInstruction": {"parts": [{"text": system_prompt}]},
            "contents": [
                {
                    "role": "user",
                    "parts": [{"text": user_prompt}],
                }
            ],
            "generationConfig": {
                "responseMimeType": "application/json",
                "responseJsonSchema": _BOOK_DRAFT_JSON_SCHEMA,
                "candidateCount": 1,
                "temperature": (
                    temperature
                    if temperature is not None
                    else self._settings.book_llm_temperature
                ),
                "maxOutputTokens": self._settings.book_max_output_tokens,
            },
        }
        return endpoint, payload, self._headers()

    def _parse_body(
        self, body: dict, request: BookGenerationRequest
    ) -> BookGenerationResponse:
        model_name = body.get("modelVersion") or self._settings.book_model

        # Provider가 안전성으로 막은 경우 평가처럼 REJECTED 결과를 만들 수 없다. 책 본문에는
        # "거절"에 해당하는 표현형이 없으므로 안전성 위반 오류로 올린다.
        prompt_feedback = body.get("promptFeedback")
        if isinstance(prompt_feedback, dict) and prompt_feedback.get("blockReason"):
            raise BookGenerationUnsafeError(
                "LLM provider blocked the book generation prompt."
            )

        candidates = body.get("candidates")
        if not isinstance(candidates, list) or not candidates:
            raise ProviderInvalidResponseError("LLM provider returned no candidates.")
        candidate = candidates[0]
        if not isinstance(candidate, dict):
            raise ProviderInvalidResponseError("LLM provider returned an invalid candidate.")

        finish_reason = candidate.get("finishReason")
        if finish_reason == "SAFETY":
            raise BookGenerationUnsafeError(
                "LLM provider blocked the generated book content."
            )
        if finish_reason == "MAX_TOKENS":
            raise ProviderInvalidResponseError(
                "LLM provider response exceeded the output token limit."
            )
        if finish_reason not in {None, "STOP"}:
            raise ProviderInvalidResponseError("LLM provider did not complete normally.")

        content = self._text_from_candidate(candidate)

        # 아래 실패는 모델이 정상 완료(STOP)한 출력의 내용 문제라 재생성 대상이다.
        # 후보 부재·비정상 finish·MAX_TOKENS는 위에서 부모 타입으로 남겨 재시도에서 제외한다.
        #
        # JSON 형식 위반과 초안 내용 위반(순번·중복·빈 문장)을 분리한다. 한 덩어리로 묶으면
        # 내용 위반이 "형식 위반"으로 분류돼 재시도 지시가 어긋난다.
        try:
            raw = json.loads(_normalize_json_content(content))
            if not isinstance(raw, dict):
                raise ValueError("provider content must be a JSON object")
        except (TypeError, ValueError, json.JSONDecodeError) as exc:
            raise BookOutputContractError(
                "LLM provider returned invalid book generation JSON."
            ) from exc

        draft = parse_draft(raw)

        # 라벨·body·집계는 여기서 서버가 계산한다(ISSUE-212). 모델이 지어낸
        # submissionId와 분량 초과만 계약 위반으로 올라오고, 나머지는 성립하지 않는다.
        try:
            return build_book_response(
                draft, request, model_name, self._settings.book_prompt_version
            )
        except BookContractViolationError:
            raise
        except (TypeError, ValueError) as exc:
            raise BookOutputContractError(
                "LLM provider returned an invalid book generation contract."
            ) from exc


def build_book_provider(
    settings: Settings,
    prompt_builder: BookPromptBuilder,
    http_client: httpx.AsyncClient,
    concurrency_gate: asyncio.Semaphore,
) -> BookProvider | None:
    if not settings.llm_configured:
        return None
    if not settings.llm_provider_supported:
        raise ProviderInvalidResponseError("Unsupported LLM provider.")
    return GeminiGmsBookProvider(
        settings,
        prompt_builder,
        http_client,
        concurrency_gate,
    )
