package com.ssafy.c206.backend.integration.similarity;

import java.time.Instant;

import com.ssafy.c206.backend.integration.ai.AiEvaluationResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Discards embeddings where no vector store is available.
 *
 * <p>test 프로필은 H2를 쓰고 sentence_embeddings 테이블도 pgvector 타입도 없다.
 * {@link FakeSentenceSimilarityService}와 짝을 이뤄 유사도 기능 전체를 비활성화한다.
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "similarity", name = "client", havingValue = "fake", matchIfMissing = true)
public class NoOpSentenceEmbeddingStore implements SentenceEmbeddingStore {

	public NoOpSentenceEmbeddingStore() {
		// similarity.client를 빠뜨린 환경에서도 기동은 성공하므로, 정책이 꺼진 사실이
		// 로그에는 반드시 드러나야 한다. 이 MR이 고치려는 상태가 정확히 "조용한 비활성화"다.
		log.warn("similarity_disabled reason=no_vector_store client=fake");
	}

	@Override
	public void save(long sentenceVersionId, AiEvaluationResult result, Instant now) {
		// 저장소가 없으므로 보관하지 않는다.
	}
}
