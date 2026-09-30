import asyncio
import json
import logging

import aio_pika
from aio_pika.connection import make_url
from fastapi import FastAPI
from fastapi.responses import JSONResponse
from pydantic import ValidationError

from app.api.routes import evaluate
from app.schemas.evaluation import EvaluationRequest, EvaluationResponse
from app.schemas.evaluation_mq import SentenceEvaluationResultEnvelope


logger = logging.getLogger("ai_service.sentence_evaluation_mq")

EXCHANGE = "sentence.evaluation"
REQUEST_QUEUE = "sentence.evaluation.request"
REQUEST_DLQ = "sentence.evaluation.request.dlq"
RESULT_QUEUE = "sentence.evaluation.result"
RESULT_DLQ = "sentence.evaluation.result.dlq"


class SentenceEvaluationMqConsumer:
    """평가 요청을 소비해 기존 HTTP 평가 경로의 결과를 발행한다(ISSUE-208).

    책 생성 소비자(book_mq)와 달리 DB에 접근하지 않는다. 평가 재료(문장·오늘의 단어
    5개)는 요청 메시지가 직접 나르고, 페이로드는 HTTP 계약(sentence-evaluation-v1)과
    같은 형태라 기존 EvaluationRequest 검증을 그대로 태운다.

    실패 분류:
    - 페이로드가 계약 위반 → 어떤 시도의 것인지도 알 수 없어 결과를 만들 수 없으므로
      DLQ로 보낸다. Spring 쪽은 stale 회수(15분)가 실패 전이·평가권 반환을 맡는다.
    - 처리 오류(LLM 미설정·일시 장애·계약 위반 응답) → 실패 envelope를 발행해 Spring이
      즉시 실패 전이·평가권 반환을 하게 한다. HTTP 경로에서도 같은 오류를 즉시 실패로
      집계했으므로 여기서 requeue로 재시도를 늘리면 오히려 의미가 달라진다. 사용자는
      폴링으로 기다리는 중이라 빠른 확정이 재시도보다 낫다.
    """

    # 의존 서비스가 아직 없을 때 재시도 간격. 배포 순서가 어긋나도 스스로 붙는다.
    _RETRY_INITIAL_SECONDS = 2.0
    _RETRY_MAX_SECONDS = 30.0

    def __init__(self, application: FastAPI):
        self.application = application
        self.connection = None
        self.channel = None
        self.exchange = None
        self._connect_task: asyncio.Task | None = None

    async def start(self) -> None:
        """설정만 즉시 검증하고 실제 연결은 백그라운드에서 재시도한다.

        브로커가 늦게 떠도 HTTP 평가·책 생성은 무관하게 살아 있어야 하므로 기동을
        막지 않는다. 설정 누락은 재시도해도 낫지 않으므로 여기서 즉시 실패시킨다.
        """
        settings = self.application.state.settings
        if not settings.evaluation_mq_configured:
            raise RuntimeError(
                "EVALUATION_MQ_ENABLED requires RABBITMQ_USER and RABBITMQ_PASSWORD"
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
                    "sentence_evaluation_mq_connect_retry reason=%s retry_in=%.1fs",
                    exception,
                    delay,
                )
                await self._release_partial_resources()
                await asyncio.sleep(delay)
                delay = min(delay * 2, self._RETRY_MAX_SECONDS)

    async def _release_partial_resources(self) -> None:
        """재시도 전에 절반만 열린 자원을 닫는다. 커넥션 누수를 막는다.

        close 실패는 삼킨다. 이 정리는 재시도 루프의 except 안에서 불리는데, 끊긴
        소켓의 close가 던진 예외가 루프 밖으로 나가면 아무도 await하지 않는 연결
        태스크가 로그 없이 죽어 컨슈머가 영영 붙지 않는다.
        """
        if self.connection is not None and not self.connection.is_closed:
            try:
                await self.connection.close()
            except Exception:
                logger.warning("sentence_evaluation_mq_close_failed", exc_info=True)
        self.connection = None
        self.channel = None
        self.exchange = None

    async def _connect(self) -> None:
        settings = self.application.state.settings
        # connect_robust 헬퍼 대신 연결 객체를 먼저 쥐고 connect를 기다린다. 헬퍼는
        # 연결이 완성돼야 참조를 돌려주는데, 그 대기 중에 close()가 태스크를 취소하면
        # RobustConnection이 CancelledError를 삼키고 참조 없는 내부 재연결 태스크만
        # 영원히 남아 이벤트 루프 종료(테스트·서비스 셧다운)가 막힌다. 참조를 먼저
        # 잡아 두면 어느 경로로 실패하든 _release_partial_resources가 함께 닫는다.
        self.connection = aio_pika.RobustConnection(
            make_url(
                host=settings.rabbitmq_host,
                port=settings.rabbitmq_port,
                login=settings.rabbitmq_user,
                password=settings.rabbitmq_password,
                virtualhost=settings.rabbitmq_vhost,
            )
        )
        await self.connection.connect()
        self.channel = await self.connection.channel(
            publisher_confirms=True,
            on_return_raises=True,
        )
        # LLM 게이트와 같은 수만 미리 가져온다. 더 가져와도 게이트 앞에서 줄만 선다.
        await self.channel.set_qos(prefetch_count=settings.llm_max_concurrency)
        self.exchange = await self.channel.declare_exchange(
            EXCHANGE, aio_pika.ExchangeType.DIRECT, durable=True
        )
        request_queue = await self._declare_queue(REQUEST_QUEUE, REQUEST_DLQ)
        await self._declare_queue(REQUEST_DLQ)
        await self._declare_queue(RESULT_QUEUE, RESULT_DLQ)
        await self._declare_queue(RESULT_DLQ)
        await request_queue.consume(self._on_message)
        logger.info("sentence_evaluation_mq_consumer_started queue=%s", REQUEST_QUEUE)

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
            request = EvaluationRequest.model_validate_json(message.body)
        except ValidationError as exception:
            # 어떤 시도(correlationId)의 것인지도 신뢰할 수 없어 실패 envelope도 만들 수 없다.
            logger.warning("sentence_evaluation_mq_request_rejected reason=%s", exception)
            await message.reject(requeue=False)
            return

        state = self.application.state
        try:
            result = await evaluate(
                request,
                state.settings,
                state.sentence_http_client,
                state.provider_concurrency_gate,
                state.embedding_concurrency_gate,
                state.provider_factory,
                state.embedding_provider_factory,
            )
            envelope = self._envelope(request, result)
        except Exception:
            # 예상 밖 예외는 재시도해도 대개 같은 결과다. Spring이 회수 타임아웃을
            # 기다리지 않도록 즉시 실패로 알린다.
            logger.exception(
                "sentence_evaluation_mq_processing_failed correlation_id=%s "
                "sentence_version_id=%s",
                request.correlationId,
                request.sentenceVersionId,
            )
            envelope = SentenceEvaluationResultEnvelope.failed(
                request.sentenceVersionId,
                str(request.correlationId),
                "AI_EVALUATION_FAILED",
                "Sentence evaluation failed unexpectedly inside the consumer.",
            )

        await self._publish_and_ack(message, envelope)

    # 발행 실패 시 결과를 손에 쥔 채 제자리에서 재시도하는 백오프. 즉시 재입큐하면
    # 이미 계산한 결과를 버리고 evaluate()(GMS 호출)를 처음부터 다시 돌게 된다.
    _PUBLISH_RETRY_DELAYS = (1.0, 2.0, 4.0)

    async def _publish_and_ack(
        self,
        message: aio_pika.IncomingMessage,
        envelope: SentenceEvaluationResultEnvelope,
    ) -> None:
        for delay in (*self._PUBLISH_RETRY_DELAYS, None):
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
                    "sentence_evaluation_mq_result_publish_failed correlation_id=%s "
                    "sentence_version_id=%s retry_in=%s",
                    envelope.correlationId,
                    envelope.sentenceVersionId,
                    delay,
                )
                if delay is not None:
                    await asyncio.sleep(delay)
                continue
            await message.ack()
            return

        # 백오프까지 소진한 발행 실패. 첫 전달이면 한 사이클 더 시도해 보고(순단 대비),
        # 재전달까지 실패하면 DLQ로 보내 뜨거운 재평가 루프를 끊는다. 결과는 유실되지만
        # Spring의 stale 회수가 실패 전이·평가권 반환으로 수습하고 원본은 DLQ에 남는다.
        if message.redelivered:
            logger.error(
                "sentence_evaluation_mq_result_dead_lettered correlation_id=%s "
                "sentence_version_id=%s",
                envelope.correlationId,
                envelope.sentenceVersionId,
            )
            await message.reject(requeue=False)
            return
        await message.nack(requeue=True)

    @staticmethod
    def _envelope(
        request: EvaluationRequest, result: EvaluationResponse | JSONResponse
    ) -> SentenceEvaluationResultEnvelope:
        if isinstance(result, EvaluationResponse):
            return SentenceEvaluationResultEnvelope.succeeded(
                request.sentenceVersionId, str(request.correlationId), result
            )
        error = json.loads(result.body)
        return SentenceEvaluationResultEnvelope.failed(
            request.sentenceVersionId,
            str(request.correlationId),
            error.get("errorCode", "AI_EVALUATION_FAILED"),
            error.get("message", "Sentence evaluation failed without a reason."),
        )
