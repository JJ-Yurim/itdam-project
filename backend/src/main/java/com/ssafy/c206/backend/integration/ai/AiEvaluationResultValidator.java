package com.ssafy.c206.backend.integration.ai;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.ssafy.c206.backend.sentence.evaluation.domain.MoodTaxonomy;

/** Validates provider output before it is allowed into the domain flow. */
public final class AiEvaluationResultValidator {

	private static final Set<String> MOOD_CATEGORIES = MoodTaxonomy.codes();

	private AiEvaluationResultValidator() {
	}

	public static void validate(AiEvaluationCommand command, AiEvaluationResult result) {
		if (result == null || result.safety() == null || result.safety().status() == null) {
			throw invalid("safety status is required.");
		}
		validateMetadata(result);

		if (result.safety().status() == SafetyStatus.REJECTED) {
			if (result.scores() != null || hasEntries(result.moods()) || hasEntries(result.usedDailyWords())) {
				throw invalid("safety-rejected results must not include scores, moods, or used words.");
			}
			return;
		}

		validateScores(result.scores());
		if (result.overallFeedback() == null || result.overallFeedback().isBlank()) {
			throw invalid("overallFeedback is required for passed safety.");
		}
		validateMoods(result.moods());
		validateUsedDailyWords(command, result.usedDailyWords());
	}

	private static void validateMetadata(AiEvaluationResult result) {
		if (isBlank(result.modelName()) || isBlank(result.promptVersion()) || isBlank(result.rubricVersion())) {
			throw invalid("modelName, promptVersion, and rubricVersion are required.");
		}
	}

	private static void validateScores(QualityScores scores) {
		if (scores == null) {
			throw invalid("all five quality scores are required.");
		}
		validateScore(scores.completeness(), "completeness");
		validateScore(scores.originality(), "originality");
		validateScore(scores.expression(), "expression");
		validateScore(scores.imagery(), "imagery");
		validateScore(scores.emotionalImpact(), "emotionalImpact");
	}

	private static void validateScore(Integer score, String fieldName) {
		if (score == null || score < 0 || score > 100) {
			throw invalid(fieldName + " must be between 0 and 100.");
		}
	}

	private static void validateMoods(List<MoodResult> moods) {
		if (moods == null || moods.size() > 3) {
			throw invalid("moods must contain at most three entries.");
		}
		Set<String> categories = new HashSet<>();
		Set<Integer> ranks = new HashSet<>();
		for (MoodResult mood : moods) {
			if (mood == null || !MOOD_CATEGORIES.contains(mood.categoryCode()) || !categories.add(mood.categoryCode())) {
				throw invalid("mood category must be a unique supported category.");
			}
			if (mood.rank() < 1 || mood.rank() > moods.size() || !ranks.add(mood.rank())) {
				throw invalid("mood ranks must be unique values from 1 to mood count.");
			}
			BigDecimal strength = mood.strength();
			if (strength == null || strength.compareTo(BigDecimal.ZERO) < 0 || strength.compareTo(BigDecimal.ONE) > 0) {
				throw invalid("mood strength must be between 0.0 and 1.0.");
			}
		}
	}

	private static void validateUsedDailyWords(AiEvaluationCommand command, List<UsedDailyWordResult> usedDailyWords) {
		if (usedDailyWords == null || usedDailyWords.size() != command.dailyWords().size()) {
			throw invalid("every requested daily word must be returned exactly once.");
		}
		Set<Long> requestedIds = new HashSet<>();
		for (DailyWordCard dailyWord : command.dailyWords()) {
			requestedIds.add(dailyWord.wordId());
		}
		Set<Long> returnedIds = new HashSet<>();
		for (UsedDailyWordResult usedDailyWord : usedDailyWords) {
			if (usedDailyWord == null || !requestedIds.contains(usedDailyWord.wordId()) || !returnedIds.add(usedDailyWord.wordId())) {
				throw invalid("usedDailyWords must be a unique subset matching requested word IDs.");
			}
		}
		if (!returnedIds.equals(requestedIds)) {
			throw invalid("usedDailyWords must include every requested word ID.");
		}
	}

	private static boolean hasEntries(List<?> values) {
		return values != null && !values.isEmpty();
	}

	private static boolean isBlank(String value) {
		return value == null || value.isBlank();
	}

	private static AiEvaluationInvalidResponseException invalid(String message) {
		return new AiEvaluationInvalidResponseException(message);
	}
}
