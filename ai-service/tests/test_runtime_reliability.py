import asyncio
import json
import logging
from uuid import uuid4

import httpx
import pytest
from fastapi.testclient import TestClient

from app.application import create_app
from app.config import Settings
from app.dependencies import get_provider_factory
from app.http_clients import build_book_http_client, build_sentence_http_client
from app.prompt import EvaluationPromptBuilder
from app.providers import GeminiGmsEvaluationProvider, ProviderTransientError
from app.schemas.evaluation import EvaluationRequest, EvaluationResponse


def runtime_settings(**overrides) -> Settings:
    values = {
        "app_name": "mvp-ai-service-test",
        "app_env": "test",
        "llm_provider": "gemini-gms",
        "llm_api_key": "secret-test-key",
        "llm_model": "gemini-test",
        "llm_base_url": "https://gms.test/v1beta",
        "llm_max_retries": 1,
        "llm_retry_base_delay_seconds": 0,
        "llm_retry_max_delay_seconds": 0,
        "prompt_version": "prompt-test",
        "rubric_version": "rubric-test",
    }
    values.update(overrides)
    return Settings(_env_file=None, **values)


def evaluation_request() -> EvaluationRequest:
    return EvaluationRequest.model_validate(
        {
            "contractVersion": "sentence-evaluation-v1",
            "correlationId": str(uuid4()),
            "sentenceVersionId": 84,
            "text": "햇빛이 스며든 고요한 방에서 별을 기다렸다.",
            "serviceDate": "2026-07-28",
            "dailyWords": [
                {
                    "wordId": word_id,
                    "word": f"단어-{word_id}",
                    "partOfSpeech": "명사",
                    "definition": f"정의-{word_id}",
                }
                for word_id in range(1, 6)
            ],
        }
    )


def provider_envelope():
    result = {
        "safety": {"status": "PASSED", "reason": None},
        "scores": {
            "completeness": 80,
            "originality": 76,
            "expression": 82,
            "imagery": 74,
            "emotionalImpact": 79,
        },
        "overallFeedback": "문장이 자연스럽습니다.",
        "moods": [],
        "usedDailyWords": [
            {"wordId": word_id, "used": word_id == 1}
            for word_id in range(1, 6)
        ],
    }
    return {
        "modelVersion": "gemini-provider-version",
        "candidates": [
            {
                "finishReason": "STOP",
                "content": {"parts": [{"text": json.dumps(result)}]},
            }
        ],
    }


def test_readiness_distinguishes_liveness_from_provider_configuration():
    unconfigured = runtime_settings(
        llm_provider=None,
        llm_api_key=None,
        llm_model=None,
        llm_base_url=None,
    )
    with TestClient(create_app(unconfigured)) as client:
        assert client.get("/health").status_code == 200
        response = client.get("/health/ready")
        assert response.status_code == 503
        assert response.json()["status"] == "NOT_READY"

    with TestClient(create_app(runtime_settings())) as client:
        response = client.get("/health/ready")
        assert response.status_code == 200
        assert response.json()["status"] == "READY"


def test_application_owns_isolated_http_clients_for_its_lifespan():
    settings = runtime_settings()
    application = create_app(settings)

    with TestClient(application) as client:
        sentence_client = application.state.sentence_http_client
        book_client = application.state.book_http_client
        client.get("/health")
        client.get("/health")
        assert application.state.sentence_http_client is sentence_client
        assert application.state.book_http_client is book_client
        assert sentence_client is not book_client
        assert sentence_client.timeout.read == settings.llm_read_timeout_seconds
        assert book_client.timeout.read == settings.book_read_timeout_seconds
        assert (
            settings.sentence_max_concurrency
            == settings.llm_max_concurrency + settings.embedding_max_concurrency
        )
        assert not sentence_client.is_closed
        assert not book_client.is_closed

    assert sentence_client.is_closed
    assert book_client.is_closed


def test_busy_book_connection_does_not_block_sentence_client():
    async def run():
        book_started = asyncio.Event()
        release_book = asyncio.Event()

        async def handle_request(reader, writer):
            request = await reader.readuntil(b"\r\n\r\n")
            if request.startswith(b"GET /book "):
                book_started.set()
                await release_book.wait()
            writer.write(
                b"HTTP/1.1 200 OK\r\nContent-Length: 2\r\nConnection: close\r\n\r\nOK"
            )
            await writer.drain()
            writer.close()
            await writer.wait_closed()

        server = await asyncio.start_server(handle_request, "127.0.0.1", 0)
        port = server.sockets[0].getsockname()[1]
        settings = runtime_settings(
            llm_max_concurrency=1,
            embedding_max_concurrency=1,
            book_max_concurrency=1,
            llm_pool_timeout_seconds=0.05,
        )

        try:
            async with build_sentence_http_client(settings) as sentence_client:
                async with build_book_http_client(settings) as book_client:
                    book_task = asyncio.create_task(
                        book_client.get(f"http://127.0.0.1:{port}/book")
                    )
                    try:
                        await asyncio.wait_for(book_started.wait(), timeout=1)
                        sentence_response = await asyncio.wait_for(
                            sentence_client.get(f"http://127.0.0.1:{port}/sentence"),
                            timeout=1,
                        )
                        assert sentence_response.status_code == 200
                    finally:
                        release_book.set()
                        await book_task
        finally:
            server.close()
            await server.wait_closed()

    asyncio.run(run())


def test_provider_retries_429_once_and_honors_retry_after():
    calls = 0
    delays = []

    def handler(_request: httpx.Request) -> httpx.Response:
        nonlocal calls
        calls += 1
        if calls == 1:
            return httpx.Response(429, headers={"Retry-After": "0.5"})
        return httpx.Response(200, json=provider_envelope())

    async def run():
        async with httpx.AsyncClient(transport=httpx.MockTransport(handler)) as client:
            provider = GeminiGmsEvaluationProvider(
                runtime_settings(llm_retry_max_delay_seconds=0.25),
                EvaluationPromptBuilder(),
                client,
                asyncio.Semaphore(1),
                sleep=lambda delay: _record_delay(delays, delay),
            )
            return await provider.evaluate(evaluation_request())

    result = asyncio.run(run())

    assert calls == 2
    assert delays == [0.25]
    assert result.safety.status == "PASSED"


async def _record_delay(delays: list[float], delay: float) -> None:
    delays.append(delay)


def test_provider_does_not_retry_beyond_configured_limit():
    calls = 0

    def handler(_request: httpx.Request) -> httpx.Response:
        nonlocal calls
        calls += 1
        return httpx.Response(503)

    async def run():
        async with httpx.AsyncClient(transport=httpx.MockTransport(handler)) as client:
            provider = GeminiGmsEvaluationProvider(
                runtime_settings(llm_max_retries=2),
                EvaluationPromptBuilder(),
                client,
                asyncio.Semaphore(1),
            )
            await provider.evaluate(evaluation_request())

    with pytest.raises(ProviderTransientError):
        asyncio.run(run())
    assert calls == 3


def test_total_request_budget_includes_provider_wait_time():
    async def handler(_request: httpx.Request) -> httpx.Response:
        await asyncio.sleep(0.05)
        return httpx.Response(200, json=provider_envelope())

    async def run():
        async with httpx.AsyncClient(transport=httpx.MockTransport(handler)) as client:
            provider = GeminiGmsEvaluationProvider(
                runtime_settings(llm_request_budget_seconds=0.01),
                EvaluationPromptBuilder(),
                client,
                asyncio.Semaphore(1),
            )
            await provider.evaluate(evaluation_request())

    with pytest.raises(ProviderTransientError, match="budget"):
        asyncio.run(run())


def test_provider_concurrency_is_bounded_by_shared_gate():
    active = 0
    maximum_active = 0

    async def handler(_request: httpx.Request) -> httpx.Response:
        nonlocal active, maximum_active
        active += 1
        maximum_active = max(maximum_active, active)
        await asyncio.sleep(0.01)
        active -= 1
        return httpx.Response(200, json=provider_envelope())

    async def run():
        async with httpx.AsyncClient(transport=httpx.MockTransport(handler)) as client:
            gate = asyncio.Semaphore(2)
            providers = [
                GeminiGmsEvaluationProvider(
                    runtime_settings(), EvaluationPromptBuilder(), client, gate
                )
                for _ in range(5)
            ]
            await asyncio.gather(
                *(provider.evaluate(evaluation_request()) for provider in providers)
            )

    asyncio.run(run())

    assert maximum_active == 2


def test_evaluation_logs_correlation_without_sentence_or_api_key(caplog):
    request = evaluation_request()

    class FakeProvider:
        async def evaluate(self, _request):
            envelope = provider_envelope()
            content = envelope["candidates"][0]["content"]["parts"][0]["text"]
            result = json.loads(content)
            result.update(
                modelName="gemini-test",
                promptVersion="prompt-test",
                rubricVersion="rubric-test",
            )
            return EvaluationResponse.model_validate(result)

    application = create_app(runtime_settings())
    application.dependency_overrides[get_provider_factory] = (
        lambda: lambda _settings, _prompt, _client, _gate: FakeProvider()
    )

    with caplog.at_level(logging.INFO, logger="ai_service.evaluation"):
        with TestClient(application) as client:
            response = client.post(
                "/internal/v1/evaluations",
                json=request.model_dump(mode="json"),
            )

    assert response.status_code == 200
    assert str(request.correlationId) in caplog.text
    assert str(request.sentenceVersionId) in caplog.text
    assert request.text not in caplog.text
    assert "secret-test-key" not in caplog.text
