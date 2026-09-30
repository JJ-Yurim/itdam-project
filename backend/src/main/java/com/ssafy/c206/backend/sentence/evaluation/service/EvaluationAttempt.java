package com.ssafy.c206.backend.sentence.evaluation.service;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import com.ssafy.c206.backend.integration.ai.AiEvaluationCommand;

record EvaluationAttempt(Long userId, Long versionId, LocalDate serviceDate, AiEvaluationCommand command,
		Map<Long, Long> dailyWordItemIds) {
	EvaluationAttempt {
		dailyWordItemIds = Map.copyOf(dailyWordItemIds);
	}
}
