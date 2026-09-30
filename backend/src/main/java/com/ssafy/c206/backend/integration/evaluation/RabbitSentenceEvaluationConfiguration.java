package com.ssafy.c206.backend.integration.evaluation;

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
 * 문장 평가 큐 토폴로지(ISSUE-208).
 *
 * <p>{@code evaluation.dispatch=rabbit}일 때만 활성화된다. 기본값(sync)에서는 리스너와
 * 토폴로지 선언이 아예 생기지 않아 브로커 없는 환경에서 연결 시도가 일어나지 않고,
 * 기존 동기 평가 경로가 그대로 동작한다.
 *
 * <p>요청 큐의 재시도는 소비자인 ai-service가, 결과 큐의 재시도는 이쪽 리스너 컨테이너
 * 설정이 담당한다. 소진 시 DLX 인자를 따라 각자의 DLQ로 흘러간다 — 책 생성과 같은 정책.
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "evaluation.dispatch", havingValue = "rabbit")
public class RabbitSentenceEvaluationConfiguration {

	@Bean
	Declarables sentenceEvaluationTopology() {
		DirectExchange exchange = new DirectExchange(SentenceEvaluationMqContract.EXCHANGE, true, false);
		Queue request = QueueBuilder.durable(SentenceEvaluationMqContract.REQUEST_QUEUE)
				.deadLetterExchange(SentenceEvaluationMqContract.EXCHANGE)
				.deadLetterRoutingKey(SentenceEvaluationMqContract.REQUEST_DLQ)
				.build();
		Queue requestDlq = QueueBuilder.durable(SentenceEvaluationMqContract.REQUEST_DLQ).build();
		Queue result = QueueBuilder.durable(SentenceEvaluationMqContract.RESULT_QUEUE)
				.deadLetterExchange(SentenceEvaluationMqContract.EXCHANGE)
				.deadLetterRoutingKey(SentenceEvaluationMqContract.RESULT_DLQ)
				.build();
		Queue resultDlq = QueueBuilder.durable(SentenceEvaluationMqContract.RESULT_DLQ).build();
		return new Declarables(exchange, request, requestDlq, result, resultDlq,
				BindingBuilder.bind(request).to(exchange).with(SentenceEvaluationMqContract.REQUEST_QUEUE),
				BindingBuilder.bind(requestDlq).to(exchange).with(SentenceEvaluationMqContract.REQUEST_DLQ),
				BindingBuilder.bind(result).to(exchange).with(SentenceEvaluationMqContract.RESULT_QUEUE),
				BindingBuilder.bind(resultDlq).to(exchange).with(SentenceEvaluationMqContract.RESULT_DLQ));
	}
}
