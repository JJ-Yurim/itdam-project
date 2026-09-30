package com.ssafy.c206.backend.sentence.evaluation.service;

import java.math.BigDecimal;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class WordBonusCalculator {

	private final EvaluationPolicy evaluationPolicy;

	public BigDecimal calculate(int usedDailyWordCount) {
		return evaluationPolicy.wordBonusFor(usedDailyWordCount);
	}
}
