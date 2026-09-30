package com.ssafy.c206.backend.integration.similarity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * 유사도 저장·비교 구현이 similarity.client 설정에 따라 짝을 이뤄 선택되는지 검증한다.
 * 한쪽만 바뀌면 벡터를 저장하면서 비교하지 않거나 그 반대가 되어 정책이 조용히 무력화된다.
 */
class SimilarityClientWiringTest {

	@Nested
	@SpringBootTest
	@ActiveProfiles("test")
	class DefaultProfile {

		@Autowired private SentenceSimilarityService similarityService;
		@Autowired private SentenceEmbeddingStore embeddingStore;

		@Test
		void fakeImplementationsAreSelectedWhenNothingIsConfigured() {
			// H2에는 vector 타입도 sentence_embeddings 테이블도 없다.
			assertThat(similarityService).isInstanceOf(FakeSentenceSimilarityService.class);
			assertThat(embeddingStore).isInstanceOf(NoOpSentenceEmbeddingStore.class);
		}
	}

	@Nested
	@SpringBootTest(properties = "similarity.client=pgvector")
	@ActiveProfiles("test")
	class PgVectorConfigured {

		@Autowired private SentenceSimilarityService similarityService;
		@Autowired private SentenceEmbeddingStore embeddingStore;

		@Test
		void pgVectorImplementationsAreSelectedTogether() {
			assertThat(similarityService).isInstanceOf(PgVectorSentenceSimilarityService.class);
			assertThat(embeddingStore).isInstanceOf(PgVectorSentenceEmbeddingStore.class);
		}
	}

	@Nested
	class ThresholdValidation {

		@Test
		void thresholdOutsideCosineRangeFailsFast() {
			// 백분율로 착각한 90이나 음수는 정책을 조용히 뒤집으므로 기동 시점에 막는다.
			assertThatThrownBy(() -> new PgVectorSentenceSimilarityService(null, 90.0))
					.isInstanceOf(IllegalArgumentException.class);
			assertThatThrownBy(() -> new PgVectorSentenceSimilarityService(null, -0.1))
					.isInstanceOf(IllegalArgumentException.class);
		}

		@Test
		void thresholdInsideCosineRangeIsAccepted() {
			assertThatCode(() -> new PgVectorSentenceSimilarityService(null, 0.90))
					.doesNotThrowAnyException();
		}
	}
}
