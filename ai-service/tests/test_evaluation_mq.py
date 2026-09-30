"""문장 평가 MQ 계약의 단위 검증(ISSUE-208). 브로커 왕복은 Spring infra 테스트가 담당한다."""

import asyncio
import json
from types import SimpleNamespace

import pytest
from fastapi.responses import JSONResponse

import app.evaluation_mq as evaluation_mq
from app.config import Settings
from app.evaluation_mq import SentenceEvaluationMqConsumer
from app.schemas.evaluation import EvaluationResponse
from app.schemas.evaluation_mq import SentenceEvaluationResultEnvelope


CORRELATION_ID = "550e8400-e29b-41d4-a716-446655440000"


@pytest.fixture
def anyio_backend():
    return "asyncio"


def request_payload() -> dict:
    return {
        "contractVersion": "sentence-evaluation-v1",
        "correlationId": CORRELATION_ID,
        "sentenceVersionId": 84,
        "text": "비가 그친 골목에 햇빛이 천천히 번졌다.",
        "serviceDate": "2026-08-07",
        "dailyWords": [
            {
                "wordId": index,
                "word": f"단어{index}",
                "partOfSpeech": "명사",
                "definition": f"정의 {index}",
            }
            for index in range(1, 6)
        ],
    }


def passed_response() -> EvaluationResponse:
    return EvaluationResponse.model_validate(
        {
            "safety": {"status": "PASSED", "reason": None},
            "scores": {
                "completeness": 80,
                "originality": 76,
                "expression": 82,
                "imagery": 74,
                "emotionalImpact": 79,
            },
            "overallFeedback": "장면과 감정이 자연스럽게 연결되어 있습니다.",
            "moods": [{"categoryCode": "CALM", "strength": 0.8, "rank": 1}],
            "usedDailyWords": [
                {"wordId": index, "used": index == 1} for index in range(1, 6)
            ],
            "modelName": "fake-evaluator",
            "promptVersion": "prompt-v1",
            "rubricVersion": "rubric-v1",
        }
    )


def test_success_envelope_wraps_the_http_response_shape():
    envelope = SentenceEvaluationResultEnvelope.succeeded(84, CORRELATION_ID, passed_response())

    dumped = json.loads(envelope.model_dump_json())
    assert dumped["contractVersion"] == "sentence-evaluation-mq-v1"
    assert dumped["sentenceVersionId"] == 84
    assert dumped["success"] is True
    # Spring AiEvaluationResult가 그대로 역직렬화하는 형태다. HTTP 응답 본문과 동일해야 한다.
    assert dumped["evaluation"]["safety"]["status"] == "PASSED"
    assert dumped["evaluation"]["scores"]["completeness"] == 80
    assert len(dumped["evaluation"]["usedDailyWords"]) == 5


def test_evaluation_mq_requires_broker_credentials_only():
    # 재료가 메시지에 실리므로 책과 달리 DB 계정 없이 브로커 계정만으로 구성이 완성된다.
    missing = Settings(evaluation_mq_enabled=True, _env_file=None)
    assert not missing.evaluation_mq_configured

    configured = Settings(
        evaluation_mq_enabled=True,
        rabbitmq_user="user",
        rabbitmq_password="password",
        book_db_user=None,
        book_db_password=None,
        _env_file=None,
    )
    assert configured.evaluation_mq_configured


@pytest.mark.anyio
async def test_consumer_startup_does_not_block_on_unavailable_broker():
    settings = Settings(
        evaluation_mq_enabled=True,
        rabbitmq_host="127.0.0.1",
        rabbitmq_port=1,
        rabbitmq_user="user",
        rabbitmq_password="password",
        _env_file=None,
    )
    application = SimpleNamespace(state=SimpleNamespace(settings=settings))
    consumer = SentenceEvaluationMqConsumer(application)

    await consumer.start()
    await asyncio.sleep(0.1)

    assert consumer._connect_task is not None
    assert not consumer._connect_task.done()
    await consumer.close()
    assert consumer.connection is None


@pytest.mark.anyio
async def test_consumer_startup_fails_fast_on_missing_credentials():
    settings = Settings(evaluation_mq_enabled=True, _env_file=None)
    application = SimpleNamespace(state=SimpleNamespace(settings=settings))

    with pytest.raises(RuntimeError):
        await SentenceEvaluationMqConsumer(application).start()


def consumer_with_state() -> tuple[SentenceEvaluationMqConsumer, SimpleNamespace]:
    state = SimpleNamespace(
        settings=Settings(_env_file=None),
        sentence_http_client=object(),
        provider_concurrency_gate=asyncio.Semaphore(1),
        embedding_concurrency_gate=asyncio.Semaphore(1),
        provider_factory=object(),
        embedding_provider_factory=object(),
    )
    return SentenceEvaluationMqConsumer(SimpleNamespace(state=state)), state


@pytest.mark.anyio
async def test_consumer_runs_the_http_evaluation_path(monkeypatch):
    consumer, state = consumer_with_state()
    captured = {}

    async def fake_evaluate(request, settings, http_client, *_gates_and_factories):
        captured["request"] = request
        captured["http_client"] = http_client
        return passed_response()

    async def fake_publish(_message, envelope):
        captured["envelope"] = envelope

    monkeypatch.setattr(evaluation_mq, "evaluate", fake_evaluate)
    monkeypatch.setattr(consumer, "_publish_and_ack", fake_publish)

    message = SimpleNamespace(body=json.dumps(request_payload()).encode("utf-8"))
    await consumer._on_message(message)

    assert captured["request"].sentenceVersionId == 84
    assert captured["http_client"] is state.sentence_http_client
    assert captured["envelope"].success is True
    assert captured["envelope"].correlationId == CORRELATION_ID
    assert captured["envelope"].evaluation.safety.status == "PASSED"


@pytest.mark.anyio
async def test_http_error_response_becomes_a_failure_envelope(monkeypatch):
    consumer, _state = consumer_with_state()
    captured = {}

    async def fake_evaluate(*_args):
        return JSONResponse(
            status_code=503,
            content={"errorCode": "LLM_PROVIDER_UNAVAILABLE", "message": "GMS timeout"},
        )

    async def fake_publish(_message, envelope):
        captured["envelope"] = envelope

    monkeypatch.setattr(evaluation_mq, "evaluate", fake_evaluate)
    monkeypatch.setattr(consumer, "_publish_and_ack", fake_publish)

    await consumer._on_message(SimpleNamespace(body=json.dumps(request_payload()).encode("utf-8")))

    envelope = captured["envelope"]
    assert envelope.success is False
    assert envelope.errorCode == "LLM_PROVIDER_UNAVAILABLE"
    assert envelope.evaluation is None
    assert envelope.sentenceVersionId == 84


@pytest.mark.anyio
async def test_unexpected_processing_error_becomes_a_failure_envelope(monkeypatch):
    consumer, _state = consumer_with_state()
    captured = {}

    async def fake_evaluate(*_args):
        raise RuntimeError("boom")

    async def fake_publish(_message, envelope):
        captured["envelope"] = envelope

    monkeypatch.setattr(evaluation_mq, "evaluate", fake_evaluate)
    monkeypatch.setattr(consumer, "_publish_and_ack", fake_publish)

    await consumer._on_message(SimpleNamespace(body=json.dumps(request_payload()).encode("utf-8")))

    assert captured["envelope"].success is False
    assert captured["envelope"].errorCode == "AI_EVALUATION_FAILED"


@pytest.mark.anyio
async def test_result_publish_retries_in_place_without_reevaluating(monkeypatch):
    """발행 순단은 계산해 둔 결과를 쥔 채 백오프 재시도한다. 즉시 재입큐로 GMS를 다시 부르지 않는다."""
    consumer, _state = consumer_with_state()
    consumer._PUBLISH_RETRY_DELAYS = (0.0, 0.0, 0.0)
    calls = {"evaluate": 0, "publish": 0, "ack": 0}

    async def fake_evaluate(*_args):
        calls["evaluate"] += 1
        return passed_response()

    class FlakyExchange:
        async def publish(self, *_args, **_kwargs):
            calls["publish"] += 1
            if calls["publish"] < 3:
                raise RuntimeError("broker hiccup")

    async def ack():
        calls["ack"] += 1

    monkeypatch.setattr(evaluation_mq, "evaluate", fake_evaluate)
    consumer.exchange = FlakyExchange()

    message = SimpleNamespace(
        body=json.dumps(request_payload()).encode("utf-8"), ack=ack, redelivered=False
    )
    await consumer._on_message(message)

    assert calls == {"evaluate": 1, "publish": 3, "ack": 1}


@pytest.mark.anyio
async def test_exhausted_result_publish_requeues_once_then_dead_letters(monkeypatch):
    consumer, _state = consumer_with_state()
    consumer._PUBLISH_RETRY_DELAYS = (0.0,)
    captured = {}

    async def fake_evaluate(*_args):
        return passed_response()

    class BrokenExchange:
        async def publish(self, *_args, **_kwargs):
            raise RuntimeError("result queue is gone")

    async def nack(requeue):
        captured["nack_requeue"] = requeue

    async def reject(requeue):
        captured["reject_requeue"] = requeue

    monkeypatch.setattr(evaluation_mq, "evaluate", fake_evaluate)
    consumer.exchange = BrokenExchange()
    body = json.dumps(request_payload()).encode("utf-8")

    # 첫 전달은 순단일 수 있으니 한 사이클 더 재입큐한다.
    await consumer._on_message(SimpleNamespace(body=body, nack=nack, redelivered=False))
    assert captured["nack_requeue"] is True

    # 재전달까지 실패하면 DLQ로 끊어 뜨거운 재평가 루프를 막는다.
    await consumer._on_message(SimpleNamespace(body=body, reject=reject, redelivered=True))
    assert captured["reject_requeue"] is False


@pytest.mark.anyio
async def test_release_partial_resources_survives_a_broken_close():
    consumer, _state = consumer_with_state()

    class BrokenConnection:
        is_closed = False

        async def close(self):
            raise OSError("socket already torn down")

    consumer.connection = BrokenConnection()

    await consumer._release_partial_resources()

    assert consumer.connection is None


@pytest.mark.anyio
async def test_contract_violating_payload_is_rejected_to_dlq():
    consumer, _state = consumer_with_state()
    captured = {}

    async def reject(requeue):
        captured["requeue"] = requeue

    message = SimpleNamespace(body=b'{"contractVersion": "sentence-evaluation-v999"}', reject=reject)
    await consumer._on_message(message)

    assert captured["requeue"] is False
