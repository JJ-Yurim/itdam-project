package com.ssafy.c206.backend.integration.evaluation;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 평가 요청을 RabbitMQ에 발행한다(ISSUE-208).
 *
 * <p>호출부가 트랜잭션 밖에서만 부른다. 책 생성 발행이 행 잠금을 잡은 트랜잭션 안에서
 * confirm을 기다리는 것과 달리, 여기서는 발행 전 준비·발행 후 마킹이 각각 짧은
 * 트랜잭션으로 분리돼 있어 confirm 5초가 DB 자원을 물지 않는다.
 *
 * <p>confirm 실패 시 예외를 올려 미발행 상태를 유지하면 릴레이가 같은 상관 ID로 다시
 * 발행한다. confirm 뒤 마킹 전에 죽어 생기는 중복 발행은 결과 적용의 멱등성으로 수렴한다.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "evaluation.dispatch", havingValue = "rabbit")
@RequiredArgsConstructor
public class RabbitSentenceEvaluationRequester implements SentenceEvaluationRequester {

	private final RabbitTemplate rabbitTemplate;

	@Override
	public void request(SentenceEvaluationRequestMessage message) {
		CorrelationData correlation = new CorrelationData(message.correlationId().toString());
		rabbitTemplate.convertAndSend(SentenceEvaluationMqContract.EXCHANGE,
				SentenceEvaluationMqContract.REQUEST_QUEUE, message, correlation);
		try {
			CorrelationData.Confirm confirm = correlation.getFuture().get(5, TimeUnit.SECONDS);
			if (!confirm.ack()) {
				throw new IllegalStateException("RabbitMQ rejected evaluation request: " + confirm.reason());
			}
			if (correlation.getReturned() != null) {
				throw new IllegalStateException("RabbitMQ returned unroutable evaluation request: "
						+ correlation.getReturned().getReplyText());
			}
		} catch (InterruptedException exception) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException("Interrupted while confirming evaluation request publication.", exception);
		} catch (ExecutionException | TimeoutException exception) {
			throw new IllegalStateException("Evaluation request publication was not confirmed.", exception);
		}
		log.info("sentence_evaluation_request_published version_id={} correlation_id={}",
				message.sentenceVersionId(), message.correlationId());
	}
}
