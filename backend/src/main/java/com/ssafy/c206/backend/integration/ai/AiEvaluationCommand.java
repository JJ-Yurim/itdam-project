package com.ssafy.c206.backend.integration.ai;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Immutable request sent to the AI evaluation boundary. */
public record AiEvaluationCommand(
	long sentenceVersionId,
	String text,
	LocalDate serviceDate,
	List<DailyWordCard> dailyWords
) {

	public AiEvaluationCommand {
		if (sentenceVersionId <= 0) {
			throw new IllegalArgumentException("sentenceVersionId must be positive.");
		}
		if (text == null || text.isBlank()) {
			throw new IllegalArgumentException("text must not be blank.");
		}
		Objects.requireNonNull(serviceDate, "serviceDate must not be null.");
		Objects.requireNonNull(dailyWords, "dailyWords must not be null.");
		if (dailyWords.size() != 5) {
			throw new IllegalArgumentException("dailyWords must contain exactly five cards.");
		}

		dailyWords = List.copyOf(dailyWords);
		Set<Long> wordIds = new HashSet<>();
		for (DailyWordCard dailyWord : dailyWords) {
			if (dailyWord == null) {
				throw new IllegalArgumentException("dailyWords must not contain null cards.");
			}
			if (!wordIds.add(dailyWord.wordId())) {
				throw new IllegalArgumentException("dailyWords must not contain duplicate wordId values.");
			}
		}
	}
}
