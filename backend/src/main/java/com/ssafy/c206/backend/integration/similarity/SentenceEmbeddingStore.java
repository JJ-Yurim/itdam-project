package com.ssafy.c206.backend.integration.similarity;

import java.time.Instant;

import com.ssafy.c206.backend.integration.ai.AiEvaluationResult;

/**
 * Stores the embedding produced alongside a completed evaluation.
 *
 * <p>{@link SentenceSimilarityService}의 쓰기 측 짝이다. 저장과 비교는 같은 벡터 저장소를
 * 전제하므로 두 경계의 구현을 같은 설정(similarity.client)으로 함께 전환한다.
 */
public interface SentenceEmbeddingStore {

	/**
	 * 평가 결과에 저장 가능한 임베딩이 있으면 보관한다.
	 * 임베딩은 부가 결과이므로 없으면 아무 것도 하지 않는다.
	 */
	void save(long sentenceVersionId, AiEvaluationResult result, Instant now);
}
