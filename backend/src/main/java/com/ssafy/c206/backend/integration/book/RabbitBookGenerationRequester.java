package com.ssafy.c206.backend.integration.book;

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
 * 책 생성 요청을 RabbitMQ에 발행한다(ISSUE-95).
 *
 * <p>호출부(BookGenerationPreparationService.requestAfterCommit)가 준비 트랜잭션
 * 커밋 후에만 부르므로, 소비자가 메시지를 받았을 때 job 행은 반드시 존재한다.
 * broker confirm 또는 라우팅 확인이 실패하면 미발행 상태를 유지하고 복구
 * 스케줄러가 같은 correlationId로 다시 발행한다. confirm 뒤 DB commit 전에
 * 프로세스가 죽어 생길 수 있는 중복 발행은 결과 적용의 멱등성으로 수렴시킨다.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "book.generation.client", havingValue = "rabbit")
@RequiredArgsConstructor
public class RabbitBookGenerationRequester implements BookGenerationRequester {

	private final RabbitTemplate rabbitTemplate;

	@Override
	public void request(BookGenerationRequestMessage message) {
		CorrelationData correlation = new CorrelationData(message.correlationId());
		rabbitTemplate.convertAndSend(BookGenerationMqContract.EXCHANGE, BookGenerationMqContract.REQUEST_QUEUE,
				message, correlation);
		try {
			CorrelationData.Confirm confirm = correlation.getFuture().get(5, TimeUnit.SECONDS);
			if (!confirm.ack()) {
				throw new IllegalStateException("RabbitMQ rejected book request: " + confirm.reason());
			}
			if (correlation.getReturned() != null) {
				throw new IllegalStateException("RabbitMQ returned unroutable book request: "
						+ correlation.getReturned().getReplyText());
			}
		} catch (InterruptedException exception) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException("Interrupted while confirming book request publication.", exception);
		} catch (ExecutionException | TimeoutException exception) {
			throw new IllegalStateException("Book request publication was not confirmed.", exception);
		}
		log.info("book_generation_request_published job_id={} correlation_id={}",
				message.jobId(), message.correlationId());
	}
}
