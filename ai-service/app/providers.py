from __future__ import annotations

import asyncio
import json
import logging
import random
import re
from collections.abc import Awaitable, Callable
from typing import Protocol
from urllib.parse import quote

import httpx

from app.config import Settings
from app.retry import retry_after_seconds
from app.schemas.evaluation import EvaluationRequest, EvaluationResponse


logger = logging.getLogger("ai_service.provider")
_JSON_CODE_FENCE = re.compile(r"^```(?:json)?\s*(.*?)\s*```$", re.IGNORECASE | re.DOTALL)
_SAFETY_REJECTION_REASON = "안전 정책에 따라 해당 문장은 평가할 수 없습니다."
_RETRYABLE_STATUS_CODES = {408, 409, 425, 429}
_EVALUATION_RESPONSE_JSON_SCHEMA = {
    "type": "object",
    "additionalProperties": False,
    "properties": {
        "safety": {
            "type": "object",
            "additionalProperties": False,
            "properties": {
                "status": {"type": "string", "enum": ["PASSED", "REJECTED"]},
                "reason": {"type": ["string", "null"]},
            },
            "required": ["status", "reason"],
        },
        "scores": {
            "anyOf": [
                {
                    "type": "object",
                    "additionalProperties": False,
                    "properties": {
                        name: {"type": "integer", "minimum": 0, "maximum": 100}
                        for name in (
                            "completeness",
                            "originality",
                            "expression",
                            "imagery",
                            "emotionalImpact",
                        )
                    },
                    "required": [
                        "completeness",
                        "originality",
                        "expression",
                        "imagery",
                        "emotionalImpact",
                    ],
                },
                {"type": "null"},
            ]
        },
        "overallFeedback": {"type": ["string", "null"]},
        "moods": {
            "type": "array",
            "maxItems": 3,
            "items": {
                "type": "object",
                "additionalProperties": False,
                "properties": {
                    "categoryCode": {
                        "type": "string",
                        "enum": [
                            "JOY",
                            "HOPE",
                            "LOVE",
                            "CALM",
                            "NOSTALGIA",
                            "SADNESS",
                            "LONELINESS",
                            "ANGER",
                            "TENSION",
                            "MYSTERY",
                        ],
                    },
                    "strength": {"type": "number", "minimum": 0, "maximum": 1},
                    "rank": {"type": "integer", "minimum": 1, "maximum": 3},
                },
                "required": ["categoryCode", "strength", "rank"],
            },
        },
        "usedDailyWords": {
            "type": "array",
            "maxItems": 5,
            "items": {
                "type": "object",
                "additionalProperties": False,
                "properties": {
                    "wordId": {"type": "integer", "minimum": 1},
                    "used": {"type": "boolean"},
                },
                "required": ["wordId", "used"],
            },
        },
    },
    "required": [
        "safety",
        "scores",
        "overallFeedback",
        "moods",
        "usedDailyWords",
    ],
}


def _normalize_json_content(content: str) -> str:
    """Remove an optional Markdown JSON fence before contract parsing."""
    normalized = content.strip()
    match = _JSON_CODE_FENCE.match(normalized)
    if match:
        return match.group(1).strip()
    return normalized


class ProviderTransientError(RuntimeError):
    """A provider or network failure that may succeed when retried."""


class ProviderInvalidResponseError(RuntimeError):
    """The provider returned a non-contract response."""


class EvaluationProvider(Protocol):
    async def evaluate(self, request: EvaluationRequest) -> EvaluationResponse:
        ...


class GeminiGmsCaller:
    """GMS Gemini generateContent 호출의 재시도·백오프·동시성 제어를 공유한다.

    문장 평가와 공동 책 생성은 프롬프트와 응답 계약만 다를 뿐 전송 계층의 요구사항이 같다.
    두 곳에 같은 재시도 규칙을 복사해 두면 한쪽만 고쳐지는 사고가 나므로 여기서 공유한다.
    """

    def __init__(
        self,
        settings: Settings,
        http_client: httpx.AsyncClient,
        concurrency_gate: asyncio.Semaphore,
        sleep: Callable[[float], Awaitable[None]] = asyncio.sleep,
        jitter: Callable[[float, float], float] = random.uniform,
    ):
        self._settings = settings
        self._http_client = http_client
        self._concurrency_gate = concurrency_gate
        self._sleep = sleep
        self._jitter = jitter

    def _generate_content_endpoint(self, model: str) -> str:
        return (
            f"{self._settings.llm_base_url.rstrip('/')}/models/"
            f"{quote(model, safe='')}:generateContent"
        )

    def _headers(self) -> dict[str, str]:
        return {
            "Content-Type": "application/json",
            "x-goog-api-key": self._settings.llm_api_key,
        }

    async def _post_with_retries(
        self,
        endpoint: str,
        payload: dict,
        headers: dict[str, str],
        correlation_id,
        read_timeout: httpx.Timeout | None = None,
        retry_truncated_envelope: bool = False,
    ) -> dict:
        """Gemini 응답 envelope을 dict로 돌려준다. 재시도 판단은 전부 여기서 끝낸다."""
        max_attempts = self._settings.llm_max_retries + 1
        # httpx는 timeout=None을 "타임아웃 없음"으로 해석하므로, 지정된 경우에만 넘긴다.
        timeout_kwargs = {} if read_timeout is None else {"timeout": read_timeout}

        for attempt in range(1, max_attempts + 1):
            try:
                response = await self._http_client.post(
                    endpoint,
                    json=payload,
                    headers=headers,
                    **timeout_kwargs,
                )
            except (httpx.TimeoutException, httpx.NetworkError) as exc:
                if attempt >= max_attempts:
                    raise ProviderTransientError("LLM provider request failed.") from exc
                logger.warning(
                    "provider_retry correlation_id=%s attempt=%d reason=network_error",
                    correlation_id,
                    attempt,
                )
                await self._sleep(self._retry_delay(attempt, None))
                continue

            if response.status_code in _RETRYABLE_STATUS_CODES or response.status_code >= 500:
                if attempt >= max_attempts:
                    raise ProviderTransientError("LLM provider returned a retryable status.")
                logger.warning(
                    "provider_retry correlation_id=%s attempt=%d status_code=%d",
                    correlation_id,
                    attempt,
                    response.status_code,
                )
                await self._sleep(
                    self._retry_delay(attempt, response.headers.get("Retry-After"))
                )
                continue

            if response.status_code < 200 or response.status_code >= 300:
                raise ProviderInvalidResponseError("LLM provider rejected the request.")

            try:
                body = response.json()
            except (TypeError, ValueError, json.JSONDecodeError) as exc:
                # GMS 프록시는 큰 응답의 envelope 앞부분을 간헐적으로 잘라 보낸다. 평가 응답은
                # 작아서 겪지 않지만 책 본문은 수천 토큰이라 실제로 노출되므로, 그쪽만 재시도한다.
                if not retry_truncated_envelope:
                    raise ProviderInvalidResponseError(
                        "LLM provider returned a non-JSON envelope."
                    ) from exc
                if attempt >= max_attempts:
                    raise ProviderTransientError(
                        "LLM provider returned a truncated envelope."
                    ) from exc
                logger.warning(
                    "provider_retry correlation_id=%s attempt=%d reason=truncated_envelope",
                    correlation_id,
                    attempt,
                )
                await self._sleep(self._retry_delay(attempt, None))
                continue

            if not isinstance(body, dict):
                raise ProviderInvalidResponseError("LLM provider returned an invalid envelope.")
            return body

        raise ProviderTransientError("LLM provider retry attempts were exhausted.")

    def _retry_delay(self, attempt: int, retry_after: str | None) -> float:
        provider_delay = retry_after_seconds(retry_after)
        if provider_delay is not None:
            return min(provider_delay, self._settings.llm_retry_max_delay_seconds)

        exponential = self._settings.llm_retry_base_delay_seconds * (2 ** (attempt - 1))
        capped = min(exponential, self._settings.llm_retry_max_delay_seconds)
        return self._jitter(0.0, capped) if capped > 0 else 0.0

    def _text_from_candidate(self, candidate: dict) -> str:
        content = candidate.get("content")
        parts = content.get("parts") if isinstance(content, dict) else None
        if not isinstance(parts, list) or not parts:
            raise ProviderInvalidResponseError("LLM provider returned no content parts.")
        text_parts = [
            part.get("text")
            for part in parts
            if isinstance(part, dict)
            and isinstance(part.get("text"), str)
            and part.get("text").strip()
        ]
        if not text_parts:
            raise ProviderInvalidResponseError("LLM provider returned empty content.")
        return "".join(text_parts)


class GeminiGmsEvaluationProvider(GeminiGmsCaller):
    """Adapter for the GMS Gemini generateContent endpoint."""

    def __init__(
        self,
        settings: Settings,
        prompt_builder,
        http_client: httpx.AsyncClient,
        concurrency_gate: asyncio.Semaphore,
        sleep: Callable[[float], Awaitable[None]] = asyncio.sleep,
        jitter: Callable[[float, float], float] = random.uniform,
    ):
        super().__init__(settings, http_client, concurrency_gate, sleep, jitter)
        self._prompt_builder = prompt_builder

    async def evaluate(self, request: EvaluationRequest) -> EvaluationResponse:
        try:
            return await asyncio.wait_for(
                self._evaluate_with_concurrency_limit(request),
                timeout=self._settings.llm_request_budget_seconds,
            )
        except asyncio.TimeoutError as exc:
            raise ProviderTransientError("LLM provider request budget was exceeded.") from exc

    async def _evaluate_with_concurrency_limit(
        self, request: EvaluationRequest
    ) -> EvaluationResponse:
        async with self._concurrency_gate:
            return await self._evaluate_with_retries(request)

    async def _evaluate_with_retries(
        self, request: EvaluationRequest
    ) -> EvaluationResponse:
        endpoint, payload, headers = self._build_request(request)
        body = await self._post_with_retries(
            endpoint, payload, headers, request.correlationId
        )
        return self._parse_body(body, request)

    def _build_request(
        self, request: EvaluationRequest
    ) -> tuple[str, dict, dict[str, str]]:
        system_prompt, user_prompt = self._prompt_builder.build(request)
        endpoint = self._generate_content_endpoint(self._settings.llm_model)
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
                "responseJsonSchema": _EVALUATION_RESPONSE_JSON_SCHEMA,
                "candidateCount": 1,
                "temperature": self._settings.llm_temperature,
                "maxOutputTokens": self._settings.llm_max_output_tokens,
            },
        }
        return endpoint, payload, self._headers()

    def _parse_body(self, body: dict, request: EvaluationRequest) -> EvaluationResponse:
        model_name = body.get("modelVersion") or self._settings.llm_model
        prompt_feedback = body.get("promptFeedback")
        if isinstance(prompt_feedback, dict) and prompt_feedback.get("blockReason"):
            return self._safety_rejected(model_name)

        candidates = body.get("candidates")
        if not isinstance(candidates, list) or not candidates:
            raise ProviderInvalidResponseError("LLM provider returned no candidates.")
        candidate = candidates[0]
        if not isinstance(candidate, dict):
            raise ProviderInvalidResponseError("LLM provider returned an invalid candidate.")

        finish_reason = candidate.get("finishReason")
        if finish_reason == "SAFETY":
            return self._safety_rejected(model_name)
        if finish_reason == "MAX_TOKENS":
            raise ProviderInvalidResponseError("LLM provider response exceeded the output token limit.")
        if finish_reason not in {None, "STOP"}:
            raise ProviderInvalidResponseError("LLM provider did not complete normally.")

        content = self._text_from_candidate(candidate)

        try:
            result = json.loads(_normalize_json_content(content))
            if not isinstance(result, dict):
                raise ValueError("provider content must be a JSON object")
            result["modelName"] = model_name
            result["promptVersion"] = self._settings.prompt_version
            result["rubricVersion"] = self._settings.rubric_version
            return EvaluationResponse.model_validate(result)
        except (TypeError, ValueError, json.JSONDecodeError) as exc:
            logger.warning(
                "provider_invalid_contract correlation_id=%s sentence_version_id=%s",
                request.correlationId,
                request.sentenceVersionId,
            )
            raise ProviderInvalidResponseError("LLM provider returned invalid evaluation JSON.") from exc

    def _safety_rejected(self, model_name: str) -> EvaluationResponse:
        return EvaluationResponse.model_validate(
            {
                "safety": {"status": "REJECTED", "reason": _SAFETY_REJECTION_REASON},
                "scores": None,
                "overallFeedback": None,
                "moods": [],
                "usedDailyWords": [],
                "modelName": model_name,
                "promptVersion": self._settings.prompt_version,
                "rubricVersion": self._settings.rubric_version,
            }
        )


def build_provider(
    settings: Settings,
    prompt_builder,
    http_client: httpx.AsyncClient,
    concurrency_gate: asyncio.Semaphore,
) -> EvaluationProvider | None:
    if not settings.llm_configured:
        return None
    if not settings.llm_provider_supported:
        raise ProviderInvalidResponseError("Unsupported LLM provider.")
    return GeminiGmsEvaluationProvider(
        settings,
        prompt_builder,
        http_client,
        concurrency_gate,
    )
