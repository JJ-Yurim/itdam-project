package com.ssafy.c206.backend.sentence.evaluation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

class EvaluationPolicyTest {

	private final EvaluationPolicy policy = EvaluationPolicy.defaults();
	private final BaseScoreCalculator baseScoreCalculator = new BaseScoreCalculator(policy);
	private final WordBonusCalculator wordBonusCalculator = new WordBonusCalculator(policy);
	private final EvaluationScoreCalculator scoreCalculator = new EvaluationScoreCalculator(
			baseScoreCalculator, wordBonusCalculator);

	@Test
	void calculatesWeightedBaseScoreWithHalfUpScaleTwo() {
		EvaluationScores scores = new EvaluationScores(
				new BigDecimal("90.01"), new BigDecimal("80.02"), new BigDecimal("70.03"),
				new BigDecimal("60.04"), new BigDecimal("50.05"));

		assertThat(baseScoreCalculator.calculate(scores)).isEqualByComparingTo("73.03");
		assertThat(baseScoreCalculator.calculate(new EvaluationScores(
				new BigDecimal("0.02"), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO)))
				.isEqualByComparingTo("0.01");
	}

	@Test
	void acceptsScoreBoundariesAndRejectsScoresOutsideRange() {
		assertThat(baseScoreCalculator.calculate(new EvaluationScores(
				BigDecimal.ZERO, BigDecimal.valueOf(100), BigDecimal.ZERO, BigDecimal.valueOf(100), BigDecimal.ZERO)))
				.isEqualByComparingTo("40.00");

		assertThatIllegalArgumentException().isThrownBy(() -> baseScoreCalculator.calculate(new EvaluationScores(
				new BigDecimal("-0.01"), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO)));
		assertThatIllegalArgumentException().isThrownBy(() -> baseScoreCalculator.calculate(new EvaluationScores(
				new BigDecimal("100.01"), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO)));
	}

	@Test
	void returnsConfiguredWordBonusForEveryDailyWordBoundary() {
		assertThat(wordBonusCalculator.calculate(0)).isEqualByComparingTo("0");
		assertThat(wordBonusCalculator.calculate(1)).isEqualByComparingTo("0");
		assertThat(wordBonusCalculator.calculate(2)).isEqualByComparingTo("5");
		assertThat(wordBonusCalculator.calculate(3)).isEqualByComparingTo("10");
		assertThat(wordBonusCalculator.calculate(4)).isEqualByComparingTo("20");
		assertThat(wordBonusCalculator.calculate(5)).isEqualByComparingTo("30");
		assertThatIllegalArgumentException().isThrownBy(() -> wordBonusCalculator.calculate(6));
	}

	@Test
	void createsPersistableScoreSnapshot() {
		EvaluationScoreCalculation result = scoreCalculator.calculate(new EvaluationScores(
				BigDecimal.valueOf(80), BigDecimal.valueOf(80), BigDecimal.valueOf(80),
				BigDecimal.valueOf(80), BigDecimal.valueOf(80)), 2);

		assertThat(result.baseScore()).isEqualByComparingTo("80.00");
		assertThat(result.wordBonus()).isEqualByComparingTo("5");
		assertThat(result.gameBaseAttack()).isEqualByComparingTo("85.00");
	}

	@Test
	void rejectsInvalidPolicyConfiguration() {
		Map<EvaluationCriterion, BigDecimal> incompleteWeights = new EnumMap<>(EvaluationCriterion.class);
		incompleteWeights.put(EvaluationCriterion.COMPLETENESS, BigDecimal.valueOf(100));

		assertThatIllegalArgumentException().isThrownBy(() -> new EvaluationPolicy(5, incompleteWeights, validBonuses()));
		assertThatIllegalArgumentException().isThrownBy(() -> new EvaluationPolicy(0, validWeights(), validBonuses()));
		assertThatIllegalArgumentException().isThrownBy(() -> new EvaluationPolicy(5, validWeights(), Map.of(0, BigDecimal.ZERO)));
	}

	private static Map<EvaluationCriterion, BigDecimal> validWeights() {
		return new EnumMap<>(EvaluationPolicy.defaults().getScoreWeights());
	}

	private static Map<Integer, BigDecimal> validBonuses() {
		return EvaluationPolicy.defaults().getWordBonuses();
	}
}
