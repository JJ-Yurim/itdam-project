package com.ssafy.c206.backend.integration.book;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 프로퍼티 미설정 시의 안전 기본값. 브로커 없는 환경에서 애플리케이션이 뜨는 것을
 * 보장하고, 실제 발행은 book.generation.client=rabbit으로 명시 전환해야 켜진다.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "book.generation.client", havingValue = "logging", matchIfMissing = true)
public class LoggingBookGenerationRequester implements BookGenerationRequester {
	@Override
	public void request(BookGenerationRequestMessage request) {
		log.info("Book generation request recorded after commit: jobId={}, correlationId={}",
				request.jobId(), request.correlationId());
	}
}
