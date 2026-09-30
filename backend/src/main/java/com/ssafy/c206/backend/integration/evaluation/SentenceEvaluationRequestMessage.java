package com.ssafy.c206.backend.integration.evaluation;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import com.ssafy.c206.backend.integration.ai.AiEvaluationCommand;
import com.ssafy.c206.backend.integration.ai.DailyWordCard;

/**
 * 평가 요청 메시지. ai-service의 HTTP {@code EvaluationRequest} 스키마와 같은 형태라
 * 컨슈머가 별도 스키마 없이 기존 검증을 그대로 태운다.
 */
public record SentenceEvaluationRequestMessage(
		String contractVersion,
		UUID correlationId,
		long sentenceVersionId,
		String text,
		LocalDate serviceDate,
		List<DailyWordCard> dailyWords) {

	public static SentenceEvaluationRequestMessage from(AiEvaluationCommand command, UUID correlationId) {
		return new SentenceEvaluationRequestMessage(SentenceEvaluationMqContract.REQUEST_CONTRACT_VERSION,
				correlationId, command.sentenceVersionId(), command.text(), command.serviceDate(),
				command.dailyWords());
	}
}
