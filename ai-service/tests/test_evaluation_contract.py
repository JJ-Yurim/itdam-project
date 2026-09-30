import asyncio
import json
import socket
from uuid import uuid4

import httpx
import pytest
from fastapi.testclient import TestClient
from pydantic import ValidationError
from pytest_socket import SocketConnectBlockedError

from app.application import create_app
from app.config import Settings
from app.dependencies import get_provider_factory
from app.prompt import EvaluationPromptBuilder
from app.providers import GeminiGmsEvaluationProvider
from app.schemas.evaluation import (
    EvaluationRequest,
    EvaluationResponse,
    MoodResult,
    QualityScores,
    SafetyResult,
    validate_used_daily_words,
)


def isolated_settings(**overrides) -> Settings:
    values = {
        "app_name": "mvp-ai-service-test",
        "app_env": "test",
        "llm_provider": None,
        "llm_api_key": None,
        "llm_model": None,
        "llm_base_url": None,
        "prompt_version": "prompt-test",
        "rubric_version": "rubric-test",
    }
    values.update(overrides)
    return Settings(_env_file=None, **values)


@pytest.fixture
def client():
    application = create_app(isolated_settings())
    with TestClient(application) as test_client:
        yield test_client


def daily_words():
    return [
        {
            "wordId": word_id,
            "word": f"word-{word_id}",
            "partOfSpeech": "명사",
            "definition": f"definition-{word_id}",
        }
        for word_id in range(1, 6)
    ]


def valid_request():
    return {
        "contractVersion": "sentence-evaluation-v1",
        "correlationId": str(uuid4()),
        "sentenceVersionId": 10,
        "text": "오늘은 조용한 문장을 썼다.",
        "serviceDate": "2026-07-28",
        "dailyWords": daily_words(),
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
            {"wordId": word_id, "used": word_id == 1}
            for word_id in range(1, 6)
        ],
        "modelName": "provider-model",
        "promptVersion": "prompt-v1",
        "rubricVersion": "rubric-v1",
    }
    values.update(overrides)
    return values


def test_evaluation_endpoint_exposes_unconfigured_boundary(client):
    response = client.post("/internal/v1/evaluations", json=valid_request())

    assert response.status_code == 503
    assert response.json() == {
        "errorCode": "LLM_NOT_CONFIGURED",
        "message": "LLM provider, API key, model, and base URL are not configured",
    }


def test_route_uses_injected_fake_provider_without_network():
    class FakeProvider:
        async def evaluate(self, _request):
            return EvaluationResponse.model_validate(passed_result())

    application = create_app(isolated_settings())
    application.dependency_overrides[get_provider_factory] = (
        lambda: lambda _settings, _prompt_builder, _http_client, _gate: FakeProvider()
    )

    with TestClient(application) as test_client:
        response = test_client.post("/internal/v1/evaluations", json=valid_request())

    assert response.status_code == 200
    assert response.json()["safety"]["status"] == "PASSED"


def test_evaluation_request_rejects_unsupported_contract_version(client):
    payload = valid_request()
    payload["contractVersion"] = "sentence-evaluation-v2"

    assert client.post("/internal/v1/evaluations", json=payload).status_code == 422


def test_evaluation_request_rejects_content_over_500_characters(client):
    payload = valid_request()
    payload["text"] = "가" * 501

    assert client.post("/internal/v1/evaluations", json=payload).status_code == 422


def test_evaluation_request_rejects_duplicate_daily_word_ids(client):
    payload = valid_request()
    payload["dailyWords"][1]["wordId"] = payload["dailyWords"][0]["wordId"]

    assert client.post("/internal/v1/evaluations", json=payload).status_code == 422


def test_evaluation_request_requires_exactly_five_daily_words(client):
    payload = valid_request()
    payload["dailyWords"] = payload["dailyWords"][:4]

    assert client.post("/internal/v1/evaluations", json=payload).status_code == 422


def test_rejected_response_requires_reason_and_forbids_evaluation_artifacts():
    rejected = EvaluationResponse(
        safety=SafetyResult(status="REJECTED", reason="unsafe"),
        scores=None,
        overallFeedback=None,
        moods=[],
        usedDailyWords=[],
        modelName="provider-model",
        promptVersion="prompt-v1",
        rubricVersion="rubric-v1",
    )

    assert rejected.safety.status == "REJECTED"

    with pytest.raises(ValidationError):
        EvaluationResponse.model_validate(
            {
                **rejected.model_dump(),
                "safety": {"status": "REJECTED", "reason": "  "},
            }
        )
    with pytest.raises(ValidationError):
        EvaluationResponse.model_validate(
            {**rejected.model_dump(), "overallFeedback": "must be null"}
        )


def test_openapi_exposes_evaluation_contract(client):
    spec = client.get("/openapi.json").json()

    assert "/internal/v1/evaluations" in spec["paths"]
    assert "EvaluationRequest" in spec["components"]["schemas"]
    assert "EvaluationResponse" in spec["components"]["schemas"]


def test_passed_response_requires_all_requested_daily_word_usage_entries():
    request = EvaluationRequest.model_validate(valid_request())
    response = EvaluationResponse.model_validate(passed_result())

    validate_used_daily_words(request, response)

    response.usedDailyWords = response.usedDailyWords[:-1]
    with pytest.raises(ValueError):
        validate_used_daily_words(request, response)


def test_gemini_provider_uses_mock_transport_and_overwrites_untrusted_metadata():
    captured_request = {}
    provider_content = passed_result(
        modelName="hallucinated-model",
        promptVersion="hallucinated-prompt",
        rubricVersion="hallucinated-rubric",
    )

    def handler(request: httpx.Request) -> httpx.Response:
        captured_request["url"] = str(request.url)
        captured_request["headers"] = request.headers
        captured_request["payload"] = json.loads(request.content)
        return httpx.Response(
            200,
            json={
                "modelVersion": "gemini-provider-version",
                "candidates": [
                    {
                        "content": {
                            "parts": [
                                {"text": f"```json\n{json.dumps(provider_content)}\n```"}
                            ]
                        }
                    }
                ],
            },
        )

    transport = httpx.MockTransport(handler)
    settings = isolated_settings(
        llm_provider="gemini-gms",
        llm_api_key="test-key",
        llm_model="gemini-2.5-flash-lite",
        llm_base_url="https://gms.test/v1beta",
    )
    request = EvaluationRequest.model_validate(valid_request())
    async def invoke_provider():
        async with httpx.AsyncClient(transport=transport) as http_client:
            provider = GeminiGmsEvaluationProvider(
                settings,
                EvaluationPromptBuilder(),
                http_client,
                asyncio.Semaphore(1),
            )
            return await provider.evaluate(request)

    response = asyncio.run(invoke_provider())

    assert captured_request["url"].endswith(
        "/models/gemini-2.5-flash-lite:generateContent"
    )
    assert captured_request["headers"]["x-goog-api-key"] == "test-key"
    assert response.modelName == "gemini-provider-version"
    assert response.promptVersion == "prompt-test"
    assert response.rubricVersion == "rubric-test"


def test_pytest_blocks_real_socket_connections():
    with pytest.raises(SocketConnectBlockedError):
        socket.socket().connect(("203.0.113.1", 443))
