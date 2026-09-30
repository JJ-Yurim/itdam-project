package com.ssafy.c206.backend.integration.ai;

/**
 * Boundary for sentence-quality evaluation providers.
 *
 * <p>Its input and output intentionally contain only immutable value objects;
 * JPA entities never cross this boundary.</p>
 *
 * <p>구현체 계약: 반환 전에 {@link AiEvaluationResultValidator}로 결과를 검증해야 한다.
 * 도메인 서비스는 이 경계를 통과한 결과를 재검증하지 않는다.</p>
 */
public interface AiEvaluationClient {

	AiEvaluationResult evaluate(AiEvaluationCommand command);
}
