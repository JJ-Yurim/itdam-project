package com.ssafy.c206.backend.sentence.evaluation.service;

import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

import lombok.Getter;

/**
 * Central MVP evaluation policy. All values here are business configuration,
 * rather than literals distributed across orchestration services.
 */
@Getter
public final class EvaluationPolicy {

	private static final BigDecimal TOTAL_WEIGHT = BigDecimal.valueOf(100);
	private static final int MAX_DAILY_WORD_COUNT = 5;

	private final int dailyEvaluationLimit;
	private final Map<EvaluationCriterion, BigDecimal> scoreWeights;
	private final Map<Integer, BigDecimal> wordBonuses;

	public EvaluationPolicy(
			int dailyEvaluationLimit,
			Map<EvaluationCriterion, BigDecimal> scoreWeights,
			Map<Integer, BigDecimal> wordBonuses
	) {
		if (dailyEvaluationLimit <= 0) {
			throw new IllegalArgumentException("dailyEvaluationLimit must be positive.");
		}
		this.dailyEvaluationLimit = dailyEvaluationLimit;
		this.scoreWeights = copyAndValidateWeights(scoreWeights);
		this.wordBonuses = copyAndValidateBonuses(wordBonuses);
	}

	public static EvaluationPolicy defaults() {
		Map<EvaluationCriterion, BigDecimal> scoreWeights = new EnumMap<>(EvaluationCriterion.class);
		scoreWeights.put(EvaluationCriterion.COMPLETENESS, BigDecimal.valueOf(25));
		scoreWeights.put(EvaluationCriterion.ORIGINALITY, BigDecimal.valueOf(25));
		scoreWeights.put(EvaluationCriterion.EXPRESSION, BigDecimal.valueOf(20));
		scoreWeights.put(EvaluationCriterion.IMAGERY, BigDecimal.valueOf(15));
		scoreWeights.put(EvaluationCriterion.EMOTIONAL_IMPACT, BigDecimal.valueOf(15));

		Map<Integer, BigDecimal> wordBonuses = Map.of(
				0, BigDecimal.ZERO,
				1, BigDecimal.ZERO,
				2, BigDecimal.valueOf(5),
				3, BigDecimal.valueOf(10),
				4, BigDecimal.valueOf(20),
				5, BigDecimal.valueOf(30));

		return new EvaluationPolicy(5, scoreWeights, wordBonuses);
	}

	public BigDecimal wordBonusFor(int usedDailyWordCount) {
		if (usedDailyWordCount < 0 || usedDailyWordCount > MAX_DAILY_WORD_COUNT) {
			throw new IllegalArgumentException("usedDailyWordCount must be between 0 and 5.");
		}
		return wordBonuses.get(usedDailyWordCount);
	}

	private static Map<EvaluationCriterion, BigDecimal> copyAndValidateWeights(
			Map<EvaluationCriterion, BigDecimal> input
	) {
		Objects.requireNonNull(input, "scoreWeights must not be null.");
		Map<EvaluationCriterion, BigDecimal> copy = new EnumMap<>(EvaluationCriterion.class);
		for (EvaluationCriterion criterion : EvaluationCriterion.values()) {
			BigDecimal weight = input.get(criterion);
			if (weight == null || weight.signum() < 0) {
				throw new IllegalArgumentException("Each score weight must be nonnegative.");
			}
			copy.put(criterion, weight);
		}
		if (copy.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add).compareTo(TOTAL_WEIGHT) != 0) {
			throw new IllegalArgumentException("Score weights must sum to 100.");
		}
		return Map.copyOf(copy);
	}

	private static Map<Integer, BigDecimal> copyAndValidateBonuses(Map<Integer, BigDecimal> input) {
		Objects.requireNonNull(input, "wordBonuses must not be null.");
		for (int usedWordCount = 0; usedWordCount <= MAX_DAILY_WORD_COUNT; usedWordCount++) {
			BigDecimal bonus = input.get(usedWordCount);
			if (bonus == null || bonus.signum() < 0) {
				throw new IllegalArgumentException("Each word bonus must be nonnegative.");
			}
		}
		return Map.copyOf(input);
	}
}
