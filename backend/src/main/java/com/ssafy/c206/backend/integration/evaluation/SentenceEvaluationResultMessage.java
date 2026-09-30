package com.ssafy.c206.backend.integration.evaluation;

import java.util.UUID;

import com.ssafy.c206.backend.integration.ai.AiEvaluationResult;

/**
 * 평가 결과 envelope. 실패도 envelope으로 도착해야 Spring이 회수 타임아웃(15분)을
 * 기다리지 않고 즉시 실패 전이·횟수 반환을 할 수 있다.
 *
 * <p>{@code evaluation}은 HTTP 경로의 응답 본문과 같은 형태다. 성공(success=true)일 때만
 * 채워지고, 안전성 거부는 성공 envelope 안의 safety.status=REJECTED로 표현된다 —
 * 거부는 처리 실패가 아니라 정상 판정이기 때문이다.
 */
public record SentenceEvaluationResultMessage(
		String contractVersion,
		UUID correlationId,
		long sentenceVersionId,
		boolean success,
		AiEvaluationResult evaluation,
		String errorCode,
		String errorMessage) {
}
