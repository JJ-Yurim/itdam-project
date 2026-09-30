package com.ssafy.c206.backend.integration.evaluation;

import com.ssafy.c206.backend.sentence.evaluation.service.EvaluationResultApplyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 문장 평가 결과를 수신해 버전 상태 전이로 반영한다(ISSUE-208).
 *
 * <p>중복·지연 결과는 apply가 멱등하게 버린다(ack). 예외가 올라오는 경우(계약 버전
 * 불일치, DB 순단)만 컨테이너 재시도를 거쳐 result DLQ로 흘러간다.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "evaluation.dispatch", havingValue = "rabbit")
@RequiredArgsConstructor
public class RabbitSentenceEvaluationResultListener {

	private final EvaluationResultApplyService applyService;

	@RabbitListener(queues = SentenceEvaluationMqContract.RESULT_QUEUE)
	public void onResult(SentenceEvaluationResultMessage message) {
		applyService.apply(message);
		log.info("sentence_evaluation_result_applied version_id={} success={} correlation_id={}",
				message.sentenceVersionId(), message.success(), message.correlationId());
	}
}
