"""문장 임베딩 생성 경계.

유사 문장 제출 차단(정책 §6.10)은 평가 완료 시점에 만들어 둔 벡터를 제출 시점에
비교하는 구조다. 따라서 임베딩은 평가와 같은 요청 안에서 1회만 생성한다.

평가에 쓰는 생성 모델은 벡터를 반환하지 않으므로 임베딩 전용 모델을 따로 호출한다.
컨테이너 메모리 제한(512MB) 때문에 로컬 모델을 올리지 않고 GMS API를 호출한다.
"""

from __future__ import annotations

import asyncio
import logging
import math
import random
from collections.abc import Awaitable, Callable
from typing import NamedTuple, Protocol

import httpx

from app.config import Settings
from app.retry import retry_after_seconds


logger = logging.getLogger("ai_service.embedding")

_RETRYABLE_STATUS_CODES = {408, 409, 425, 429}


class EmbeddingUnavailableError(RuntimeError):
    """임베딩을 생성하지 못했다.

    임베딩은 평가의 부가 결과이므로, 호출자는 이 예외를 잡아 임베딩 없이
    평가 응답을 성공 반환해야 한다.
    """


class EmbeddingResult(NamedTuple):
    vector: list[float]
    model: str
    dimension: int


class EmbeddingProvider(Protocol):
    async def embed(self, text: str) -> EmbeddingResult:
        ...


class OpenAiCompatibleEmbeddingProvider:
    """GMS가 프록시하는 OpenAI 호환 `/embeddings` 엔드포인트 어댑터."""

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

    async def embed(self, text: str) -> EmbeddingResult:
        # 임베딩은 평가가 성공한 뒤에 실행되므로, 재시도·백오프가 길어지면 이미 성공한
        # 평가 응답이 Backend read timeout에 걸려 유실된다. 전체 예산을 짧게 강제한다.
        try:
            return await asyncio.wait_for(
                self._embed_with_concurrency_limit(text),
                timeout=self._settings.embedding_request_budget_seconds,
            )
        except asyncio.TimeoutError as exc:
            raise EmbeddingUnavailableError("Embedding request budget was exceeded.") from exc

    async def _embed_with_concurrency_limit(self, text: str) -> EmbeddingResult:
        async with self._concurrency_gate:
            return await self._embed_within_budget(text)

    async def _embed_within_budget(self, text: str) -> EmbeddingResult:
        endpoint = f"{self._settings.embedding_base_url.rstrip('/')}/embeddings"
        payload = {
            "model": self._settings.embedding_model,
            "input": text,
            # 차원을 명시해 pgvector 컬럼 정의와 항상 일치시킨다.
            "dimensions": self._settings.embedding_dimensions,
        }
        headers = {
            "Content-Type": "application/json",
            "Authorization": f"Bearer {self._settings.embedding_api_key}",
        }
        max_attempts = self._settings.llm_max_retries + 1

        for attempt in range(1, max_attempts + 1):
            try:
                response = await self._http_client.post(endpoint, json=payload, headers=headers)
            except httpx.RequestError as exc:
                # TimeoutException·NetworkError뿐 아니라 ProtocolError·ProxyError·
                # UnsupportedProtocol까지 포함해야 프록시 이상이 평가 응답을 깨지 않는다.
                if attempt >= max_attempts:
                    raise EmbeddingUnavailableError("Embedding request failed.") from exc
                await self._sleep(self._retry_delay(attempt, None))
                continue

            if response.status_code in _RETRYABLE_STATUS_CODES or response.status_code >= 500:
                if attempt >= max_attempts:
                    raise EmbeddingUnavailableError(
                        "Embedding provider returned a retryable status."
                    )
                await self._sleep(
                    self._retry_delay(attempt, response.headers.get("Retry-After"))
                )
                continue

            if response.status_code < 200 or response.status_code >= 300:
                raise EmbeddingUnavailableError("Embedding provider rejected the request.")

            return self._parse(response)

        raise EmbeddingUnavailableError("Embedding retry attempts were exhausted.")

    def _retry_delay(self, attempt: int, retry_after: str | None) -> float:
        provider_delay = retry_after_seconds(retry_after)
        if provider_delay is not None:
            return min(provider_delay, self._settings.llm_retry_max_delay_seconds)

        exponential = self._settings.llm_retry_base_delay_seconds * (2 ** (attempt - 1))
        capped = min(exponential, self._settings.llm_retry_max_delay_seconds)
        return self._jitter(0.0, capped) if capped > 0 else 0.0

    def _parse(self, response: httpx.Response) -> EmbeddingResult:
        try:
            body = response.json()
            vector = body["data"][0]["embedding"]
        except (TypeError, ValueError, KeyError, IndexError) as exc:
            raise EmbeddingUnavailableError(
                "Embedding provider returned an invalid envelope."
            ) from exc

        if not isinstance(vector, list) or not vector:
            raise EmbeddingUnavailableError("Embedding provider returned an empty vector.")

        try:
            values = [float(value) for value in vector]
        except (TypeError, ValueError) as exc:
            raise EmbeddingUnavailableError(
                "Embedding vector contains non-numeric values."
            ) from exc

        expected = self._settings.embedding_dimensions
        if len(values) < expected:
            # 짧은 벡터는 복구할 방법이 없다. vector(N) 컬럼에 넣을 수 없으므로 거부한다.
            raise EmbeddingUnavailableError(
                f"Embedding is shorter than the configured dimension: "
                f"expected {expected}, got {len(values)}."
            )
        if len(values) > expected:
            # 프록시가 dimensions 파라미터를 전달하지 않으면 모델 기본 차원이 돌아온다.
            # text-embedding-3 계열은 Matryoshka 학습이라 앞에서 잘라도 의미가 보존된다.
            logger.warning(
                "embedding_truncated expected=%d received=%d model=%s",
                expected,
                len(values),
                self._settings.embedding_model,
            )
            values = values[:expected]

        # 절단 여부와 무관하게 항상 단위 벡터로 저장한다. 두 경로의 노름이 달라지면
        # 나중에 내적(<#>)이나 L2(<->) 연산자로 바꿀 때 서로 비교할 수 없게 된다.
        values = _to_unit_vector(values)

        # 모델명은 저장 후 유사도 비교 대상을 같은 모델로 제한하는 데 쓰인다.
        model = body.get("model") or self._settings.embedding_model
        return EmbeddingResult(vector=values, model=model, dimension=expected)


def _to_unit_vector(values: list[float]) -> list[float]:
    magnitude = math.sqrt(sum(value * value for value in values))
    if magnitude == 0.0:
        raise EmbeddingUnavailableError("Embedding vector has zero magnitude.")
    return [value / magnitude for value in values]


def build_embedding_provider(
    settings: Settings,
    http_client: httpx.AsyncClient,
    concurrency_gate: asyncio.Semaphore,
) -> EmbeddingProvider | None:
    """임베딩 설정이 없으면 None을 반환해 평가가 임베딩 없이 동작하게 둔다."""
    if not settings.embedding_configured:
        return None
    return OpenAiCompatibleEmbeddingProvider(settings, http_client, concurrency_gate)
