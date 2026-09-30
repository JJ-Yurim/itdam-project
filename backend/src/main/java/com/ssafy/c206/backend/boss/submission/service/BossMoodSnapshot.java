package com.ssafy.c206.backend.boss.submission.service;

import java.math.BigDecimal;

/** Immutable boss-mood input, deliberately independent of JPA entities. */
public record BossMoodSnapshot(String moodCode, SubmissionMoodRole role, BigDecimal weight) {

	public BossMoodSnapshot {
		moodCode = SentenceMoodSnapshot.requireCode(moodCode);
		if (role == null) {
			throw new IllegalArgumentException("role must not be null.");
		}
		if (weight == null || weight.signum() < 0) {
			throw new IllegalArgumentException("weight must not be negative.");
		}
	}
}
