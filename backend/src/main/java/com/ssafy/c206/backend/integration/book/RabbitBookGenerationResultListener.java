package com.ssafy.c206.backend.integration.book;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 책 생성 결과를 수신해 상태 전이로 반영한다(ISSUE-95).
 *
 * <p>같은 방향의 중복 결과는 handler가 멱등 처리한다. 계약 위반, 모르는 job,
 * 상반된 종결 결과와 DB 일시 장애는 모두 예외를 올려 컨테이너 재시도에 맡기고,
 * 소진되면 DLX를 따라 result DLQ로 보낸다. 잘못된 결과를 ACK 후 유실하지 않기
 * 위한 정책이다.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "book.generation.client", havingValue = "rabbit")
@RequiredArgsConstructor
public class RabbitBookGenerationResultListener {

	private final BookGenerationResultHandler resultHandler;

	@RabbitListener(queues = BookGenerationMqContract.RESULT_QUEUE)
	public void onResult(BookGenerationResultMessage message) {
		resultHandler.handle(message.toResult());
		log.info("book_generation_result_applied job_id={} success={} correlation_id={}",
				message.jobId(), message.success(), message.correlationId());
	}
}
