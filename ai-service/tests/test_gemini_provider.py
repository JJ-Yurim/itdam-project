import asyncio
import json
from uuid import uuid4

import httpx
import pytest

from app.config import Settings
from app.prompt import EvaluationPromptBuilder
from app.providers import (
    GeminiGmsEvaluationProvider,
    ProviderInvalidResponseError,
    ProviderTransientError,
)
from app.schemas.evaluation import EvaluationRequest


def settings() -> Settings:
    return Settings(
        _env_file=None,
        app_env="test",
        llm_provider="gemini-gms",
        llm_api_key="test-key",
        llm_model="gemini-2.5-flash-lite",
        llm_base_url="https://gms.test/v1beta",
        llm_temperature=0.1,
        llm_max_output_tokens=1024,
        prompt_version="prompt-test",
        rubric_version="rubric-test",
    )


def evaluation_request() -> EvaluationRequest:
    return EvaluationRequest.model_validate(
        {
            "contractVersion": "sentence-evaluation-v1",
            "correlationId": str(uuid4()),
            "sentenceVersionId": 84,
            "text": "햇빛이 고요한 방에 스며들었다.",
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


def passed_payload():
    return {
        "safety": {"status": "PASSED", "reason": None},
        "scores": {
            "completeness": 80,
            "originality": 76,
            "expression": 82,
            "imagery": 74,
            "emotionalImpact": 79,
        },
        "overallFeedback": "문장 흐름이 자연스럽습니다.",
        "moods": [{"categoryCode": "CALM", "strength": 0.9, "rank": 1}],
        "usedDailyWords": [
            {"wordId": word_id, "used": word_id == 1}
            for word_id in range(1, 6)
        ],
    }


def provider_for(response_factory, captured=None):
    captured = captured if captured is not None else {}

    def handler(request: httpx.Request) -> httpx.Response:
        captured["payload"] = json.loads(request.content)
        return response_factory(request)

    transport = httpx.MockTransport(handler)
    http_client = httpx.AsyncClient(transport=transport)
    return GeminiGmsEvaluationProvider(
        settings(),
        EvaluationPromptBuilder(),
        http_client,
        asyncio.Semaphore(1),
    )


def evaluate(provider):
    async def invoke():
        try:
            return await provider.evaluate(evaluation_request())
        finally:
            await provider._http_client.aclose()

    return asyncio.run(invoke())


def test_request_separates_system_instruction_and_uses_structured_json():
    captured = {}
    provider = provider_for(
        lambda _request: httpx.Response(
            200,
            json={
                "modelVersion": "gemini-provider-version",
                "candidates": [
                    {
                        "finishReason": "STOP",
                        "content": {"parts": [{"text": json.dumps(passed_payload())}]},
                    }
                ],
            },
        ),
        captured,
    )

    result = evaluate(provider)

    payload = captured["payload"]
    assert "문장 평가자" in payload["systemInstruction"]["parts"][0]["text"]
    user_input = json.loads(payload["contents"][0]["parts"][0]["text"])
    assert user_input["sentenceVersionId"] == 84
    assert payload["contents"][0]["role"] == "user"
    assert payload["generationConfig"]["responseMimeType"] == "application/json"
    assert payload["generationConfig"]["candidateCount"] == 1
    assert payload["generationConfig"]["temperature"] == 0.1
    schema_properties = payload["generationConfig"]["responseJsonSchema"]["properties"]
    assert "modelName" not in schema_properties
    assert result.modelName == "gemini-provider-version"


@pytest.mark.parametrize(
    "envelope",
    [
        {"promptFeedback": {"blockReason": "SAFETY"}},
        {
            "candidates": [
                {
                    "finishReason": "SAFETY",
                    "content": {"parts": []},
                }
            ]
        },
    ],
)
def test_provider_safety_block_is_normalized_to_rejected(envelope):
    provider = provider_for(
        lambda _request: httpx.Response(
            200,
            json={"modelVersion": "gemini-provider-version", **envelope},
        )
    )

    result = evaluate(provider)

    assert result.safety.status == "REJECTED"
    assert result.safety.reason
    assert result.scores is None
    assert result.moods == []
    assert result.usedDailyWords == []


@pytest.mark.parametrize(
    ("envelope", "message"),
    [
        ({}, "no candidates"),
        (
            {"candidates": [{"finishReason": "STOP", "content": {"parts": []}}]},
            "no content parts",
        ),
        (
            {
                "candidates": [
                    {
                        "finishReason": "STOP",
                        "content": {"parts": [{"text": "not-json"}]},
                    }
                ]
            },
            "invalid evaluation JSON",
        ),
        (
            {
                "candidates": [
                    {
                        "finishReason": "MAX_TOKENS",
                        "content": {"parts": [{"text": "{"}]},
                    }
                ]
            },
            "output token limit",
        ),
    ],
)
def test_invalid_provider_envelopes_are_rejected(envelope, message):
    provider = provider_for(lambda _request: httpx.Response(200, json=envelope))

    with pytest.raises(ProviderInvalidResponseError, match=message):
        evaluate(provider)


@pytest.mark.parametrize("status_code", [408, 409, 425, 429, 500, 503])
def test_retryable_provider_statuses_are_transient(status_code):
    provider = provider_for(lambda _request: httpx.Response(status_code))

    with pytest.raises(ProviderTransientError):
        evaluate(provider)


def test_non_retryable_provider_4xx_is_invalid_response():
    provider = provider_for(lambda _request: httpx.Response(400))

    with pytest.raises(ProviderInvalidResponseError):
        evaluate(provider)
