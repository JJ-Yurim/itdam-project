package com.ssafy.c206.backend.sentence.evaluation.service;

import java.math.BigDecimal;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class EvaluationScoreCalculator {

	private final BaseScoreCalculator baseScoreCalculator;
	private final WordBonusCalculator wordBonusCalculator;

	public EvaluationScoreCalculation calculate(EvaluationScores scores, int usedDailyWordCount) {
		BigDecimal baseScore = baseScoreCalculator.calculate(scores);
		BigDecimal wordBonus = wordBonusCalculator.calculate(usedDailyWordCount);
		return new EvaluationScoreCalculation(baseScore, wordBonus, baseScore.add(wordBonus));
	}
}
