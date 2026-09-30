import asyncio
import json
from uuid import uuid4

import httpx
import pytest
from fastapi.testclient import TestClient
from pydantic import ValidationError

from app.application import create_app
from app.config import Settings
from app.dependencies import get_embedding_provider_factory, get_provider_factory
from app.embeddings import (
    EmbeddingResult,
    EmbeddingUnavailableError,
    OpenAiCompatibleEmbeddingProvider,
    build_embedding_provider,
)
from app.schemas.evaluation import EvaluationResponse


DIMENSIONS = 768


def isolated_settings(**overrides) -> Settings:
    values = {
        "app_name": "mvp-ai-service-test",
        "app_env": "test",
        "llm_provider": None,
        "llm_api_key": None,
        "llm_model": None,
        "llm_base_url": None,
        "embedding_model": None,
        "embedding_base_url": None,
        "embedding_api_key": None,
        "embedding_dimensions": DIMENSIONS,
        "prompt_version": "prompt-test",
        "rubric_version": "rubric-test",
    }
    values.update(overrides)
    return Settings(_env_file=None, **values)


def valid_request():
    return {
        "contractVersion": "sentence-evaluation-v1",
        "correlationId": str(uuid4()),
        "sentenceVersionId": 10,
        "text": "오늘은 조용한 문장을 썼다.",
        "serviceDate": "2026-07-28",
        "dailyWords": [
            {
                "wordId": word_id,
                "word": f"word-{word_id}",
                "partOfSpeech": "명사",
                "definition": f"definition-{word_id}",
            }
            for word_id in range(1, 6)
        ],
    }


def passed_result(**overrides):
    values = {
        "safety": {"status": "PASSED", "reason": None},
        "scores": {
            "completeness": 80,
            "originality": 76,
            "expression": 82,
            "imagery": 74,
            "emotionalImpact": 79,
        },
        "overallFeedback": "feedback",
        "moods": [],
        "usedDailyWords": [
            {"wordId": word_id, "used": word_id == 1} for word_id in range(1, 6)
        ],
        "modelName": "provider-model",
        "promptVersion": "prompt-v1",
        "rubricVersion": "rubric-v1",
    }
    values.update(overrides)
    return values


def rejected_result(**overrides):
    values = {
        "safety": {"status": "REJECTED", "reason": "unsafe"},
        "scores": None,
        "overallFeedback": None,
        "moods": [],
        "usedDailyWords": [],
        "modelName": "provider-model",
        "promptVersion": "prompt-v1",
        "rubricVersion": "rubric-v1",
    }
    values.update(overrides)
    return values


class StubEmbeddingProvider:
    def __init__(self, result=None, failure=None):
        self._result = result
        self._failure = failure
        self.calls = 0

    async def embed(self, text: str) -> EmbeddingResult:
        self.calls += 1
        if self._failure is not None:
            raise self._failure
        return self._result


def application_with(evaluation_result, embedding_provider):
    class StubEvaluationProvider:
        async def evaluate(self, _request):
            return EvaluationResponse.model_validate(evaluation_result)

    application = create_app(isolated_settings())
    application.dependency_overrides[get_provider_factory] = (
        lambda: lambda _settings, _prompt_builder, _http_client, _gate: StubEvaluationProvider()
    )
    application.dependency_overrides[get_embedding_provider_factory] = (
        lambda: lambda _settings, _http_client, _gate: embedding_provider
    )
    return application


def sample_embedding():
    return EmbeddingResult(
        vector=[0.01] * DIMENSIONS,
        model="text-embedding-3-small",
        dimension=DIMENSIONS,
    )


def test_passed_evaluation_includes_embedding():
    application = application_with(passed_result(), StubEmbeddingProvider(sample_embedding()))

    with TestClient(application) as client:
        response = client.post("/internal/v1/evaluations", json=valid_request())

    assert response.status_code == 200
    body = response.json()
    assert len(body["embedding"]) == DIMENSIONS
    assert body["embeddingModel"] == "text-embedding-3-small"
    assert body["embeddingDimension"] == DIMENSIONS


def test_rejected_evaluation_skips_embedding_entirely():
    provider = StubEmbeddingProvider(sample_embedding())
    application = application_with(rejected_result(), provider)

    with TestClient(application) as client:
        response = client.post("/internal/v1/evaluations", json=valid_request())

    assert response.status_code == 200
    assert response.json()["embedding"] is None
    # 제출 자격이 없는 문장이므로 임베딩 호출 자체가 일어나지 않아야 한다.
    assert provider.calls == 0


def test_embedding_failure_keeps_evaluation_successful():
    provider = StubEmbeddingProvider(failure=EmbeddingUnavailableError("boom"))
    application = application_with(passed_result(), provider)

    with TestClient(application) as client:
        response = client.post("/internal/v1/evaluations", json=valid_request())

    assert response.status_code == 200
    body = response.json()
    assert body["safety"]["status"] == "PASSED"
    assert body["embedding"] is None
    assert body["embeddingModel"] is None
    assert provider.calls == 1


def test_unconfigured_embedding_returns_evaluation_without_vector():
    application = application_with(passed_result(), None)

    with TestClient(application) as client:
        response = client.post("/internal/v1/evaluations", json=valid_request())

    assert response.status_code == 200
    assert response.json()["embedding"] is None


def test_embedding_length_must_match_declared_dimension():
    with pytest.raises(ValidationError):
        EvaluationResponse.model_validate(
            passed_result(
                embedding=[0.1] * 10,
                embeddingModel="text-embedding-3-small",
                embeddingDimension=DIMENSIONS,
            )
        )


def test_embedding_metadata_requires_a_vector():
    with pytest.raises(ValidationError):
        EvaluationResponse.model_validate(
            passed_result(embeddingModel="text-embedding-3-small", embeddingDimension=DIMENSIONS)
        )


def test_rejected_response_must_not_carry_an_embedding():
    with pytest.raises(ValidationError):
        EvaluationResponse.model_validate(
            rejected_result(
                embedding=[0.1] * DIMENSIONS,
                embeddingModel="text-embedding-3-small",
                embeddingDimension=DIMENSIONS,
            )
        )


def test_embedding_provider_calls_openai_compatible_endpoint():
    captured = {}

    def handler(request: httpx.Request) -> httpx.Response:
        captured["url"] = str(request.url)
        captured["headers"] = request.headers
        captured["payload"] = json.loads(request.content)
        return httpx.Response(
            200,
            json={
                "model": "text-embedding-3-small",
                "data": [{"index": 0, "embedding": [0.5] * DIMENSIONS}],
            },
        )

    settings = isolated_settings(
        embedding_model="text-embedding-3-small",
        embedding_base_url="https://gms.test/v1",
        embedding_api_key="embed-key",
    )

    async def invoke():
        async with httpx.AsyncClient(transport=httpx.MockTransport(handler)) as http_client:
            provider = OpenAiCompatibleEmbeddingProvider(
                settings,
                http_client,
                asyncio.Semaphore(settings.embedding_max_concurrency),
            )
            return await provider.embed("조용한 문장")

    result = asyncio.run(invoke())

    assert captured["url"] == "https://gms.test/v1/embeddings"
    assert captured["headers"]["authorization"] == "Bearer embed-key"
    assert captured["payload"]["model"] == "text-embedding-3-small"
    assert captured["payload"]["input"] == "조용한 문장"
    assert captured["payload"]["dimensions"] == DIMENSIONS
    assert result.model == "text-embedding-3-small"
    assert result.dimension == DIMENSIONS
    assert len(result.vector) == DIMENSIONS


def test_embedding_provider_concurrency_is_bounded_by_its_own_gate():
    active = 0
    maximum_active = 0

    async def handler(_request: httpx.Request) -> httpx.Response:
        nonlocal active, maximum_active
        active += 1
        maximum_active = max(maximum_active, active)
        await asyncio.sleep(0.01)
        active -= 1
        return httpx.Response(
            200,
            json={
                "model": "text-embedding-3-small",
                "data": [{"embedding": [0.5] * DIMENSIONS}],
            },
        )

    settings = isolated_settings(
        embedding_model="text-embedding-3-small",
        embedding_base_url="https://gms.test/v1",
        embedding_api_key="embed-key",
        embedding_max_concurrency=2,
    )

    async def run():
        async with httpx.AsyncClient(
            transport=httpx.MockTransport(handler)
        ) as http_client:
            provider = OpenAiCompatibleEmbeddingProvider(
                settings,
                http_client,
                asyncio.Semaphore(settings.embedding_max_concurrency),
            )
            await asyncio.gather(
                *(provider.embed(f"sentence-{index}") for index in range(5))
            )

    asyncio.run(run())

    assert maximum_active == 2


def embed_with_vector(vector):
    def handler(_request: httpx.Request) -> httpx.Response:
        return httpx.Response(200, json={"data": [{"embedding": vector}]})

    settings = isolated_settings(
        embedding_model="text-embedding-3-small",
        embedding_base_url="https://gms.test/v1",
        embedding_api_key="embed-key",
    )

    async def invoke():
        async with httpx.AsyncClient(transport=httpx.MockTransport(handler)) as http_client:
            provider = OpenAiCompatibleEmbeddingProvider(
                settings,
                http_client,
                asyncio.Semaphore(settings.embedding_max_concurrency),
            )
            return await provider.embed("조용한 문장")

    return asyncio.run(invoke())


def test_embedding_provider_rejects_vector_shorter_than_configured_dimension():
    with pytest.raises(EmbeddingUnavailableError):
        embed_with_vector([0.5] * 512)


def test_embedding_provider_truncates_native_dimension_to_configured_dimension():
    # GMS 프록시가 dimensions를 전달하지 않아 모델 기본 차원(1536)이 돌아오는 경우다.
    result = embed_with_vector([0.5] * 1536)

    assert result.dimension == DIMENSIONS
    assert len(result.vector) == DIMENSIONS
    # 잘라낸 뒤 정규화하므로 단위 벡터가 된다.
    assert sum(value * value for value in result.vector) == pytest.approx(1.0)


def test_embedding_provider_rejects_zero_magnitude_vector():
    with pytest.raises(EmbeddingUnavailableError):
        embed_with_vector([0.0] * 1536)


def embed_with_handler(handler, **setting_overrides):
    """대기 없이 재시도 경로를 검증하기 위해 sleep을 가로챈다."""
    slept: list[float] = []

    async def fake_sleep(seconds: float) -> None:
        slept.append(seconds)

    settings = isolated_settings(
        embedding_model="text-embedding-3-small",
        embedding_base_url="https://gms.test/v1",
        embedding_api_key="embed-key",
        **setting_overrides,
    )

    async def invoke():
        async with httpx.AsyncClient(transport=httpx.MockTransport(handler)) as http_client:
            provider = OpenAiCompatibleEmbeddingProvider(
                settings,
                http_client,
                asyncio.Semaphore(settings.embedding_max_concurrency),
                sleep=fake_sleep,
            )
            return await provider.embed("조용한 문장")

    return invoke, slept


def ok_response():
    return httpx.Response(200, json={"data": [{"embedding": [0.5] * DIMENSIONS}]})


def test_embedding_provider_retries_429_once_and_honours_retry_after():
    calls: list[int] = []

    def handler(_request: httpx.Request) -> httpx.Response:
        calls.append(1)
        if len(calls) == 1:
            return httpx.Response(429, headers={"Retry-After": "1"}, json={})
        return ok_response()

    invoke, slept = embed_with_handler(handler)
    result = asyncio.run(invoke())

    assert len(calls) == 2
    assert slept == [1.0]
    assert len(result.vector) == DIMENSIONS


def test_embedding_provider_caps_retry_after_at_configured_maximum():
    calls: list[int] = []

    def handler(_request: httpx.Request) -> httpx.Response:
        calls.append(1)
        if len(calls) == 1:
            return httpx.Response(503, headers={"Retry-After": "600"}, json={})
        return ok_response()

    invoke, slept = embed_with_handler(handler, llm_retry_max_delay_seconds=2.0)
    asyncio.run(invoke())

    assert slept == [2.0]


def test_embedding_provider_fails_after_exhausting_retries():
    calls: list[int] = []

    def handler(_request: httpx.Request) -> httpx.Response:
        calls.append(1)
        return httpx.Response(500, json={})

    invoke, _ = embed_with_handler(handler, llm_max_retries=1)

    with pytest.raises(EmbeddingUnavailableError):
        asyncio.run(invoke())
    assert len(calls) == 2


def test_embedding_provider_does_not_retry_client_errors():
    calls: list[int] = []

    def handler(_request: httpx.Request) -> httpx.Response:
        calls.append(1)
        return httpx.Response(401, json={"error": "invalid key"})

    invoke, _ = embed_with_handler(handler)

    with pytest.raises(EmbeddingUnavailableError):
        asyncio.run(invoke())
    # 잘못된 키로 쿼터를 태우지 않도록 4xx는 즉시 포기한다.
    assert len(calls) == 1


def test_embedding_provider_wraps_transport_errors_that_are_not_timeouts():
    def handler(_request: httpx.Request) -> httpx.Response:
        # 프록시가 응답을 중간에 끊는 상황. TimeoutException·NetworkError가 아니다.
        raise httpx.RemoteProtocolError("peer closed connection", request=None)

    invoke, _ = embed_with_handler(handler, llm_max_retries=0)

    with pytest.raises(EmbeddingUnavailableError):
        asyncio.run(invoke())


def test_embedding_provider_gives_up_when_request_budget_is_exceeded():
    async def slow_sleep(_seconds: float) -> None:
        await asyncio.sleep(0.2)

    def handler(_request: httpx.Request) -> httpx.Response:
        return httpx.Response(503, json={})

    settings = isolated_settings(
        embedding_model="text-embedding-3-small",
        embedding_base_url="https://gms.test/v1",
        embedding_api_key="embed-key",
        llm_max_retries=3,
        embedding_request_budget_seconds=0.05,
    )

    async def invoke():
        async with httpx.AsyncClient(transport=httpx.MockTransport(handler)) as http_client:
            provider = OpenAiCompatibleEmbeddingProvider(
                settings,
                http_client,
                asyncio.Semaphore(settings.embedding_max_concurrency),
                sleep=slow_sleep,
            )
            return await provider.embed("조용한 문장")

    with pytest.raises(EmbeddingUnavailableError):
        asyncio.run(invoke())


def test_route_survives_provider_exceptions_outside_the_embedding_error_type():
    class ExplodingEmbeddingProvider:
        async def embed(self, _text: str):
            raise httpx.InvalidURL("bad base url")

    application = application_with(passed_result(), ExplodingEmbeddingProvider())

    with TestClient(application) as client:
        response = client.post("/internal/v1/evaluations", json=valid_request())

    # 이미 과금된 평가 결과를 임베딩 실패로 버리지 않는다.
    assert response.status_code == 200
    assert response.json()["safety"]["status"] == "PASSED"
    assert response.json()["embedding"] is None


def test_embedding_provider_rejects_invalid_envelope():
    def handler(_request: httpx.Request) -> httpx.Response:
        return httpx.Response(200, json={"unexpected": "shape"})

    settings = isolated_settings(
        embedding_model="text-embedding-3-small",
        embedding_base_url="https://gms.test/v1",
        embedding_api_key="embed-key",
    )

    async def invoke():
        async with httpx.AsyncClient(transport=httpx.MockTransport(handler)) as http_client:
            provider = OpenAiCompatibleEmbeddingProvider(
                settings,
                http_client,
                asyncio.Semaphore(settings.embedding_max_concurrency),
            )
            return await provider.embed("조용한 문장")

    with pytest.raises(EmbeddingUnavailableError):
        asyncio.run(invoke())


def test_builder_returns_none_when_embedding_is_not_configured():
    async def invoke(settings):
        async with httpx.AsyncClient(
            transport=httpx.MockTransport(lambda _request: httpx.Response(200, json={}))
        ) as http_client:
            return build_embedding_provider(
                settings,
                http_client,
                asyncio.Semaphore(settings.embedding_max_concurrency),
            )

    assert asyncio.run(invoke(isolated_settings())) is None
    configured = isolated_settings(
        embedding_model="text-embedding-3-small",
        embedding_base_url="https://gms.test/v1",
        embedding_api_key="embed-key",
    )
    assert asyncio.run(invoke(configured)) is not None


def test_health_reports_embedding_configuration():
    with TestClient(create_app(isolated_settings())) as client:
        assert client.get("/health").json()["embeddingConfigured"] is False

    configured = isolated_settings(
        embedding_model="text-embedding-3-small",
        embedding_base_url="https://gms.test/v1",
        embedding_api_key="embed-key",
    )
    with TestClient(create_app(configured)) as client:
        assert client.get("/health").json()["embeddingConfigured"] is True


def test_openapi_exposes_embedding_fields():
    with TestClient(create_app(isolated_settings())) as client:
        schema = client.get("/openapi.json").json()["components"]["schemas"]["EvaluationResponse"]

    for field in ("embedding", "embeddingModel", "embeddingDimension"):
        assert field in schema["properties"]
