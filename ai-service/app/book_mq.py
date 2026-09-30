import asyncio
import json
import logging

import aio_pika
import asyncpg
from fastapi import FastAPI
from fastapi.responses import JSONResponse
from pydantic import ValidationError

from app.api.routes import generate_book
from app.book_materials import (
    BookMaterialsError,
    create_materials_pool,
    load_book_generation_request,
)
from app.schemas.book import BookGenerationResponse
from app.schemas.book_mq import BookGenerationDispatchMessage, BookGenerationResultEnvelope


logger = logging.getLogger("ai_service.book_generation_mq")

EXCHANGE = "book.generation"
REQUEST_QUEUE = "book.generation.request"
REQUEST_DLQ = "book.generation.request.dlq"
RESULT_QUEUE = "book.generation.result"
RESULT_DLQ = "book.generation.result.dlq"


class BookGenerationMqConsumer:
    """실행 지시(jobId)를 소비해 DB에서 재료를 읽고 기존 생성 경로의 결과를 발행한다.

    실패 분류:
    - 페이로드 자체가 계약 위반(jobId조차 모름) → 결과를 만들 수 없으므로 DLQ로 보낸다.
    - 재료의 결정적 오류(모르는 job, correlation 불일치, 제출 없음) → 실패 envelope를
      발행해 Spring이 30분 타임아웃을 기다리지 않고 즉시 FAILED로 전이하게 한다.
    - DB 일시 장애 → requeue로 재시도한다. DB가 죽어 있으면 Spring도 함께 죽어 있으므로
      무한 재시도가 새 요청을 굶기지 않는다.
    """

    # 의존 서비스가 아직 없을 때 재시도 간격. 배포 순서가 어긋나도 스스로 붙는다.
    _RETRY_INITIAL_SECONDS = 2.0
    _RETRY_MAX_SECONDS = 30.0

    def __init__(self, application: FastAPI):
        self.application = application
        self.connection = None
        self.channel = None
        self.exchange = None
        self.pool: asyncpg.Pool | None = None
        self._connect_task: asyncio.Task | None = None

    async def start(self) -> None:
        """설정만 즉시 검증하고 실제 연결은 백그라운드에서 재시도한다.

        RabbitMQ나 Backend DB가 아직 준비되지 않았다고 해서 기동을 막지 않는다. 배포
        파이프라인이 backend와 ai-service로 분리돼 있어 어느 쪽이 먼저 뜰지 정해져
        있지 않고(SELECT 전용 계정은 backend 마이그레이션이 만든다), 무엇보다 문장
        평가 API는 책 생성과 무관하므로 이 의존성 때문에 함께 죽어서는 안 된다.
        설정 누락은 재시도해도 낫지 않으므로 여기서 즉시 실패시킨다.
        """
        settings = self.application.state.settings
        if not settings.book_mq_configured:
            raise RuntimeError(
                "BOOK_MQ_ENABLED requires RABBITMQ_USER, RABBITMQ_PASSWORD, "
                "BOOK_DB_USER, and BOOK_DB_PASSWORD"
            )
        self._connect_task = asyncio.create_task(self._connect_with_retry())

    async def _connect_with_retry(self) -> None:
        delay = self._RETRY_INITIAL_SECONDS
        while True:
            try:
                await self._connect()
                return
            except asyncio.CancelledError:
                raise
            except Exception as exception:
                logger.warning(
                    "book_generation_mq_connect_retry reason=%s retry_in=%.1fs",
                    exception,
                    delay,
                )
                await self._release_partial_resources()
                await asyncio.sleep(delay)
                delay = min(delay * 2, self._RETRY_MAX_SECONDS)

    async def _release_partial_resources(self) -> None:
        """재시도 전에 절반만 열린 자원을 닫는다. 커넥션 누수를 막는다."""
        if self.connection is not None and not self.connection.is_closed:
            await self.connection.close()
        if self.pool is not None:
            await self.pool.close()
        self.connection = None
        self.channel = None
        self.exchange = None
        self.pool = None

    async def _connect(self) -> None:
        settings = self.application.state.settings
        self.pool = await create_materials_pool(settings)
        self.connection = await aio_pika.connect_robust(
            host=settings.rabbitmq_host,
            port=settings.rabbitmq_port,
            login=settings.rabbitmq_user,
            password=settings.rabbitmq_password,
            virtualhost=settings.rabbitmq_vhost,
        )
        self.channel = await self.connection.channel(
            publisher_confirms=True,
            on_return_raises=True,
        )
        await self.channel.set_qos(prefetch_count=settings.book_max_concurrency)
        self.exchange = await self.channel.declare_exchange(
            EXCHANGE, aio_pika.ExchangeType.DIRECT, durable=True
        )
        request_queue = await self._declare_queue(REQUEST_QUEUE, REQUEST_DLQ)
        await self._declare_queue(REQUEST_DLQ)
        await self._declare_queue(RESULT_QUEUE, RESULT_DLQ)
        await self._declare_queue(RESULT_DLQ)
        await request_queue.consume(self._on_message)
        logger.info("book_generation_mq_consumer_started queue=%s", REQUEST_QUEUE)

    async def close(self) -> None:
        if self._connect_task is not None and not self._connect_task.done():
            self._connect_task.cancel()
            try:
                await self._connect_task
            except asyncio.CancelledError:
                pass
        await self._release_partial_resources()

    async def _declare_queue(self, name: str, dead_letter: str | None = None):
        arguments = None
        if dead_letter is not None:
            arguments = {
                "x-dead-letter-exchange": EXCHANGE,
                "x-dead-letter-routing-key": dead_letter,
            }
        queue = await self.channel.declare_queue(
            name, durable=True, arguments=arguments
        )
        await queue.bind(self.exchange, routing_key=name)
        return queue

    async def _on_message(self, message: aio_pika.IncomingMessage) -> None:
        try:
            dispatch = BookGenerationDispatchMessage.model_validate_json(message.body)
        except ValidationError as exception:
            # jobId조차 신뢰할 수 없어 실패 envelope도 만들 수 없다.
            logger.warning("book_generation_mq_request_rejected reason=%s", exception)
            await message.reject(requeue=False)
            return

        try:
            request = await load_book_generation_request(
                self.pool, dispatch.jobId, dispatch.correlationId
            )
        except BookMaterialsError as exception:
            logger.warning(
                "book_generation_mq_materials_rejected job_id=%s code=%s reason=%s",
                dispatch.jobId,
                exception.code,
                exception,
            )
            await self._publish_and_ack(
                message,
                BookGenerationResultEnvelope.failed(
                    dispatch.jobId, dispatch.correlationId, exception.code, str(exception)
                ),
            )
            return
        except Exception:
            logger.exception(
                "book_generation_mq_materials_unavailable job_id=%s", dispatch.jobId
            )
            await message.nack(requeue=True)
            return

        try:
            result = await generate_book(
                request,
                self.application.state.settings,
                self.application.state.book_http_client,
                self.application.state.book_concurrency_gate,
                self.application.state.book_provider_factory,
                self.application.state.safety_filter,
            )
            envelope = self._envelope(request, result)
        except Exception:
            # 예상 밖 예외는 재시도해도 대개 같은 결과다. Spring이 타임아웃을 기다리지
            # 않도록 실패로 알리고, 원본 메시지는 조사용으로 DLQ에 남기지 않는다.
            logger.exception(
                "book_generation_mq_processing_failed correlation_id=%s job_id=%s",
                request.correlationId,
                request.jobId,
            )
            envelope = BookGenerationResultEnvelope.failed(
                dispatch.jobId,
                dispatch.correlationId,
                "BOOK_GENERATION_FAILED",
                "Book generation failed unexpectedly inside the consumer.",
            )

        await self._publish_and_ack(message, envelope)

    async def _publish_and_ack(
        self, message: aio_pika.IncomingMessage, envelope: BookGenerationResultEnvelope
    ) -> None:
        try:
            await self.exchange.publish(
                aio_pika.Message(
                    body=envelope.model_dump_json().encode("utf-8"),
                    content_type="application/json",
                    delivery_mode=aio_pika.DeliveryMode.PERSISTENT,
                    correlation_id=envelope.correlationId,
                    message_id=envelope.correlationId,
                ),
                routing_key=RESULT_QUEUE,
                mandatory=True,
            )
        except Exception:
            logger.exception(
                "book_generation_mq_result_publish_failed correlation_id=%s job_id=%s",
                envelope.correlationId,
                envelope.jobId,
            )
            await message.nack(requeue=True)
            return

        await message.ack()

    @staticmethod
    def _envelope(
        request, result: BookGenerationResponse | JSONResponse
    ) -> BookGenerationResultEnvelope:
        if isinstance(result, BookGenerationResponse):
            return BookGenerationResultEnvelope.succeeded(
                request.jobId, str(request.correlationId), result
            )
        error = json.loads(result.body)
        return BookGenerationResultEnvelope.failed(
            request.jobId,
            str(request.correlationId),
            error.get("errorCode", "BOOK_GENERATION_FAILED"),
            error.get("message", "Book generation failed without a reason."),
        )
