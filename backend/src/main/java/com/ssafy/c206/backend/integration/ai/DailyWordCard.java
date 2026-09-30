package com.ssafy.c206.backend.integration.ai;

/** A single, already-issued word meaning card. */
public record DailyWordCard(
	long wordId,
	String word,
	String partOfSpeech,
	String definition
) {

	public DailyWordCard {
		if (wordId <= 0) {
			throw new IllegalArgumentException("wordId must be positive.");
		}
		if (word == null || word.isBlank()) {
			throw new IllegalArgumentException("word must not be blank.");
		}
		if (partOfSpeech == null || partOfSpeech.isBlank()) {
			throw new IllegalArgumentException("partOfSpeech must not be blank.");
		}
		if (definition == null || definition.isBlank()) {
			throw new IllegalArgumentException("definition must not be blank.");
		}
	}
}
