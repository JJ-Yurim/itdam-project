package com.ssafy.c206.backend.sentence.evaluation.service;

import java.math.BigDecimal;
import java.math.RoundingMode;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class BaseScoreCalculator {

	private static final BigDecimal ONE_HUNDRED = BigDecimal.valueOf(100);

	private final EvaluationPolicy evaluationPolicy;

	public BigDecimal calculate(EvaluationScores scores) {
		if (scores == null) {
			throw new IllegalArgumentException("scores must not be null.");
		}
		BigDecimal total = BigDecimal.ZERO;
		for (EvaluationCriterion criterion : EvaluationCriterion.values()) {
			BigDecimal score = scores.asMap().get(criterion);
			validateScore(score);
			BigDecimal weight = evaluationPolicy.getScoreWeights().get(criterion);
			total = total.add(score.multiply(weight).divide(ONE_HUNDRED));
		}
		return total.setScale(2, RoundingMode.HALF_UP);
	}

	private static void validateScore(BigDecimal score) {
		if (score == null || score.signum() < 0 || score.compareTo(ONE_HUNDRED) > 0) {
			throw new IllegalArgumentException("Each AI score must be between 0 and 100.");
		}
	}
}
