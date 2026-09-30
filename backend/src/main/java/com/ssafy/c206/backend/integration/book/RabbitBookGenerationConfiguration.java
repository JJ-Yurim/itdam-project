package com.ssafy.c206.backend.integration.book;

import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 책 생성 큐 토폴로지(ISSUE-95).
 *
 * <p>{@code book.generation.client=rabbit}일 때만 활성화된다. 다른 값이면 리스너와
 * 토폴로지 선언이 아예 생기지 않아 브로커 없는 환경(테스트, 로컬 기본값)에서
 * 연결 시도가 일어나지 않는다.
 *
 * <p>DLQ는 Spring과 ai-service 중 먼저 뜨는 쪽이 선언해도 같도록 인자까지 계약에
 * 포함된 것으로 본다. 요청 큐의 재시도 정책은 소비자인 ai-service가 결정하고
 * (ISSUE-122), 결과 큐의 재시도는 이쪽 리스너 컨테이너 설정(application
 * properties의 listener retry)이 담당한다. 재시도 소진 시 reject되면 DLX 인자를
 * 따라 각자의 DLQ로 흘러간다.
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "book.generation.client", havingValue = "rabbit")
public class RabbitBookGenerationConfiguration {

	// JSON 변환기는 RabbitJsonMessageConfiguration이 공용으로 제공한다. 여기서 따로
	// 선언하면 평가 모드와 함께 켜졌을 때 빈이 둘이 되어 자동설정이 기본 변환기로
	// 되돌아간다.

	@Bean
	Declarables bookGenerationTopology() {
		DirectExchange exchange = new DirectExchange(BookGenerationMqContract.EXCHANGE, true, false);
		Queue request = QueueBuilder.durable(BookGenerationMqContract.REQUEST_QUEUE)
				.deadLetterExchange(BookGenerationMqContract.EXCHANGE)
				.deadLetterRoutingKey(BookGenerationMqContract.REQUEST_DLQ)
				.build();
		Queue requestDlq = QueueBuilder.durable(BookGenerationMqContract.REQUEST_DLQ).build();
		Queue result = QueueBuilder.durable(BookGenerationMqContract.RESULT_QUEUE)
				.deadLetterExchange(BookGenerationMqContract.EXCHANGE)
				.deadLetterRoutingKey(BookGenerationMqContract.RESULT_DLQ)
				.build();
		Queue resultDlq = QueueBuilder.durable(BookGenerationMqContract.RESULT_DLQ).build();
		return new Declarables(exchange, request, requestDlq, result, resultDlq,
				BindingBuilder.bind(request).to(exchange).with(BookGenerationMqContract.REQUEST_QUEUE),
				BindingBuilder.bind(requestDlq).to(exchange).with(BookGenerationMqContract.REQUEST_DLQ),
				BindingBuilder.bind(result).to(exchange).with(BookGenerationMqContract.RESULT_QUEUE),
				BindingBuilder.bind(resultDlq).to(exchange).with(BookGenerationMqContract.RESULT_DLQ));
	}
}
