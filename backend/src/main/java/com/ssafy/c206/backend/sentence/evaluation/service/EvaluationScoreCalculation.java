package com.ssafy.c206.backend.sentence.evaluation.service;

import java.math.BigDecimal;

/**
 * Immutable score snapshot to persist when a sentence evaluation passes.
 */
public record EvaluationScoreCalculation(
		BigDecimal baseScore,
		BigDecimal wordBonus,
		BigDecimal gameBaseAttack
) {
}
