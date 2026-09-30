package com.ssafy.c206.backend.boss.submission.service;

import java.math.BigDecimal;

/** Immutable sentence-mood input, deliberately independent of JPA entities. */
public record SentenceMoodSnapshot(String moodCode, BigDecimal strength) {

	public SentenceMoodSnapshot {
		moodCode = requireCode(moodCode);
		if (strength == null || strength.signum() < 0 || strength.compareTo(BigDecimal.ONE) > 0) {
			throw new IllegalArgumentException("strength must be between 0 and 1.");
		}
	}

	static String requireCode(String value) {
		if (value == null || value.trim().isBlank()) {
			throw new IllegalArgumentException("moodCode must not be blank.");
		}
		return value.trim();
	}
}
