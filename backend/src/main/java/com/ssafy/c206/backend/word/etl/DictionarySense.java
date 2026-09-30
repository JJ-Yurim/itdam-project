package com.ssafy.c206.backend.word.etl;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Getter;

/**
 * One runtime word candidate flattened from one dictionary Sense.
 *
 * <p>The DTO intentionally has no database annotations. DATA-001 only
 * defines the ETL output contract; persistence is owned by BE-005.</p>
 */
@Getter
@Builder
public class DictionarySense {

	private final String word;
	private final String partOfSpeech;
	private final String definition;
	private final String example;

	@JsonCreator
	public DictionarySense(
			@JsonProperty("word") String word,
			@JsonProperty("partOfSpeech") String partOfSpeech,
			@JsonProperty("definition") String definition,
			@JsonProperty("example") String example) {
		this.word = word;
		this.partOfSpeech = partOfSpeech;
		this.definition = definition;
		this.example = example;
	}
}
