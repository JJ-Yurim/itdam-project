package com.ssafy.c206.backend.integration.similarity;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Deterministic in-memory similarity adapter. It deliberately does not emulate
 * embeddings, thresholds, or pgvector behaviour.
 *
 * <p>test 프로필은 H2를 쓰므로 pgvector 구현을 올릴 수 없다. 이 구현을 제거하면 H2 기반
 * 테스트가 전부 깨지므로 기본 구현으로 유지한다.
 */
@Component
@ConditionalOnProperty(prefix = "similarity", name = "client", havingValue = "fake", matchIfMissing = true)
public class FakeSentenceSimilarityService implements SentenceSimilarityService {

	private final ConcurrentMap<SimilarityKey, Boolean> configuredResults = new ConcurrentHashMap<>();

	@Override
	public boolean isSimilar(long userId, long bossRunId, long sentenceVersionId) {
		return configuredResults.getOrDefault(new SimilarityKey(userId, bossRunId, sentenceVersionId), false);
	}

	public void configure(long userId, long bossRunId, long sentenceVersionId, boolean similar) {
		SimilarityKey key = new SimilarityKey(userId, bossRunId, sentenceVersionId);
		if (similar) {
			configuredResults.put(key, true);
		} else {
			configuredResults.remove(key);
		}
	}

	public void clear() {
		configuredResults.clear();
	}

	private record SimilarityKey(long userId, long bossRunId, long sentenceVersionId) {
	}
}
