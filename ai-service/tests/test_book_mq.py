"""책 생성 MQ 계약의 단위 검증. 브로커·DB 왕복은 실인프라 E2E가 담당한다."""

import asyncio
import json
from types import SimpleNamespace

import pytest
from fastapi.responses import JSONResponse
from pydantic import ValidationError

import app.book_mq as book_mq
from app.book_mq import BookGenerationMqConsumer
from app.config import Settings
from app.schemas.book_mq import BookGenerationDispatchMessage


@pytest.fixture
def anyio_backend():
    return "asyncio"


def test_dispatch_message_carries_only_the_execution_order():
    message = BookGenerationDispatchMessage.model_validate(
        {
            "contractVersion": "book-generation-v1",
            "correlationId": "550e8400-e29b-41d4-a716-446655440000",
            "jobId": 12,
        }
    )

    assert message.jobId == 12


def test_dispatch_message_rejects_materials_payload():
    # 재료는 메시지에 실리지 않는다(ADR-007). 옛 계약(재료 포함)이 오면 명시적으로 거부한다.
    with pytest.raises(ValidationError):
        BookGenerationDispatchMessage.model_validate(
            {
                "contractVersion": "book-generation-v1",
                "correlationId": "550e8400-e29b-41d4-a716-446655440000",
                "jobId": 12,
                "boss": {"name": "보스", "description": "설명", "moods": []},
                "submissions": [],
            }
        )


@pytest.mark.anyio
async def test_consumer_startup_does_not_block_on_unavailable_dependencies():
    """배포 순서가 어긋나 DB·브로커가 아직 없어도 기동은 성공해야 한다.

    두 파이프라인이 분리돼 있어 ai-service가 backend보다 먼저 뜰 수 있고, 그때는
    SELECT 전용 계정이 아직 없다. 평가 API까지 함께 죽지 않도록 연결은 백그라운드에서
    재시도한다.
    """
    settings = Settings(
        book_mq_enabled=True,
        rabbitmq_host="127.0.0.1",
        rabbitmq_port=1,
        rabbitmq_user="user",
        rabbitmq_password="password",
        book_db_host="127.0.0.1",
        book_db_port=1,
        book_db_user="mvp_ai_readonly",
        book_db_password="secret",
        _env_file=None,
    )
    application = SimpleNamespace(state=SimpleNamespace(settings=settings))
    consumer = BookGenerationMqConsumer(application)

    await consumer.start()
    await asyncio.sleep(0.1)

    assert consumer._connect_task is not None
    assert not consumer._connect_task.done()
    await consumer.close()
    assert consumer.pool is None


@pytest.mark.anyio
async def test_consumer_startup_fails_fast_on_missing_credentials():
    settings = Settings(
        book_mq_enabled=True,
        rabbitmq_user="user",
        rabbitmq_password="password",
        book_db_user=None,
        book_db_password=None,
        _env_file=None,
    )
    application = SimpleNamespace(state=SimpleNamespace(settings=settings))

    with pytest.raises(RuntimeError):
        await BookGenerationMqConsumer(application).start()


def test_book_mq_requires_db_credentials():
    settings = Settings(
        book_mq_enabled=True,
        rabbitmq_user="user",
        rabbitmq_password="password",
        book_db_user=None,
        book_db_password=None,
        _env_file=None,
    )

    assert not settings.book_mq_configured

    configured = Settings(
        book_mq_enabled=True,
        rabbitmq_user="user",
        rabbitmq_password="password",
        book_db_user="mvp_ai_readonly",
        book_db_password="secret",
        _env_file=None,
    )

    assert configured.book_mq_configured


@pytest.mark.anyio
async def test_consumer_uses_the_book_http_client(monkeypatch):
    correlation_id = "550e8400-e29b-41d4-a716-446655440000"
    book_http_client = object()
    application = SimpleNamespace(
        state=SimpleNamespace(
            settings=Settings(_env_file=None),
            book_http_client=book_http_client,
            book_concurrency_gate=asyncio.Semaphore(1),
            book_provider_factory=object(),
            safety_filter=None,
        )
    )
    consumer = BookGenerationMqConsumer(application)
    captured = {}

    async def fake_load(_pool, job_id, request_correlation_id):
        assert job_id == 12
        assert request_correlation_id == correlation_id
        return SimpleNamespace(jobId=job_id, correlationId=request_correlation_id)

    async def fake_generate(_request, _settings, http_client, *_args):
        captured["http_client"] = http_client
        return JSONResponse(
            status_code=503,
            content={"errorCode": "TEST", "message": "test response"},
        )

    async def fake_publish(_message, envelope):
        captured["envelope"] = envelope

    monkeypatch.setattr(book_mq, "load_book_generation_request", fake_load)
    monkeypatch.setattr(book_mq, "generate_book", fake_generate)
    monkeypatch.setattr(consumer, "_publish_and_ack", fake_publish)

    message = SimpleNamespace(
        body=json.dumps(
            {
                "contractVersion": "book-generation-v1",
                "correlationId": correlation_id,
                "jobId": 12,
            }
        ).encode("utf-8")
    )
    await consumer._on_message(message)

    assert captured["http_client"] is book_http_client
    assert captured["envelope"].success is False
