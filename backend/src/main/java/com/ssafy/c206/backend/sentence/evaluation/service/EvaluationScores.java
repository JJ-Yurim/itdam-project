package com.ssafy.c206.backend.sentence.evaluation.service;

import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.Map;

/**
 * Transport-neutral score values used by {@link BaseScoreCalculator}.
 * AI adapters are responsible for mapping their response contract to this value.
 */
public record EvaluationScores(
		BigDecimal completeness,
		BigDecimal originality,
		BigDecimal expression,
		BigDecimal imagery,
		BigDecimal emotionalImpact
) {

	public Map<EvaluationCriterion, BigDecimal> asMap() {
		Map<EvaluationCriterion, BigDecimal> scores = new EnumMap<>(EvaluationCriterion.class);
		scores.put(EvaluationCriterion.COMPLETENESS, completeness);
		scores.put(EvaluationCriterion.ORIGINALITY, originality);
		scores.put(EvaluationCriterion.EXPRESSION, expression);
		scores.put(EvaluationCriterion.IMAGERY, imagery);
		scores.put(EvaluationCriterion.EMOTIONAL_IMPACT, emotionalImpact);
		return scores;
	}
}
