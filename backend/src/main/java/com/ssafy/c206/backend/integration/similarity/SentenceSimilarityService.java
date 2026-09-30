package com.ssafy.c206.backend.integration.similarity;

/**
 * Checks a candidate sentence against a user's prior submissions in one boss run.
 * The caller is responsible for ownership and evaluation-state validation.
 */
public interface SentenceSimilarityService {

	boolean isSimilar(long userId, long bossRunId, long sentenceVersionId);
}
