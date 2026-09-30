package com.ssafy.c206.backend.word.etl;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;

/**
 * Streaming parser for the Korean Basic Dictionary lexical-resource JSON.
 *
 * <p>Only one LexicalEntry is materialised at a time. The parser never reads
 * the 1GB source document into a root JsonNode or a collection.</p>
 */
@RequiredArgsConstructor
public class DictionaryEtlParser {

	private static final String LEXICAL_ENTRY = "LexicalEntry";
	private static final String SENSE = "Sense";

	private final ObjectMapper objectMapper;

	public EtlStatistics parse(Path source, Consumer<DictionarySense> sink) throws IOException {
		Objects.requireNonNull(source, "source");
		try (InputStream input = Files.newInputStream(source)) {
			return parse(input, sink);
		}
	}

	public EtlStatistics parse(InputStream input, Consumer<DictionarySense> sink) throws IOException {
		Objects.requireNonNull(input, "input");
		Objects.requireNonNull(sink, "sink");
		EtlStatistics statistics = new EtlStatistics();

		try (JsonParser parser = objectMapper.getFactory().createParser(input)) {
			JsonToken token;
			while ((token = parser.nextToken()) != null) {
				if (token == JsonToken.FIELD_NAME && LEXICAL_ENTRY.equals(parser.currentName())) {
					consumeEntries(parser, parser.nextToken(), sink, statistics);
				}
			}
		}
		return statistics;
	}

	private void consumeEntries(JsonParser parser, JsonToken valueToken,
		Consumer<DictionarySense> sink, EtlStatistics statistics) throws IOException {
		if (valueToken == JsonToken.START_ARRAY) {
			while (parser.nextToken() != JsonToken.END_ARRAY) {
				if (parser.currentToken() == JsonToken.START_OBJECT) {
					consumeEntry(parser.readValueAsTree(), sink, statistics);
				} else {
					parser.skipChildren();
					statistics.malformed();
				}
			}
			return;
		}
		if (valueToken == JsonToken.START_OBJECT) {
			consumeEntry(parser.readValueAsTree(), sink, statistics);
			return;
		}
		parser.skipChildren();
		statistics.malformed();
	}

	private void consumeEntry(JsonNode entry, Consumer<DictionarySense> sink,
		EtlStatistics statistics) {
		statistics.entrySeen();
		String word = featureValue(entry.path("Lemma").path("feat"), "writtenForm");
		String partOfSpeech = featureValue(entry.path("feat"), "partOfSpeech");
		JsonNode senses = entry.get(SENSE);
		if (senses == null || senses.isNull()) {
			statistics.dropped("missing_sense");
			return;
		}

		if (senses.isArray()) {
			Set<String> emittedKeys = new HashSet<>();
			for (JsonNode sense : senses) {
				consumeSense(sense, word, partOfSpeech, emittedKeys, sink, statistics);
			}
		} else {
			consumeSense(senses, word, partOfSpeech, new HashSet<>(), sink, statistics);
		}
	}

	private void consumeSense(JsonNode sense, String word, String partOfSpeech,
		Set<String> emittedKeys, Consumer<DictionarySense> sink, EtlStatistics statistics) {
		statistics.senseSeen();
		if (sense == null || !sense.isObject()) {
			statistics.dropped("malformed_sense");
			return;
		}
		if (isBlank(word)) {
			statistics.dropped("missing_word");
			return;
		}
		if (isBlank(partOfSpeech)) {
			statistics.dropped("missing_part_of_speech");
			return;
		}
		String definition = featureValue(sense.get("feat"), "definition");
		if (isBlank(definition)) {
			statistics.dropped("missing_definition");
			return;
		}
		String normalizedWord = word.trim();
		String normalizedPartOfSpeech = partOfSpeech.trim();
		String normalizedDefinition = definition.trim();
		if (!emittedKeys.add(normalizedWord + '\u0000' + normalizedPartOfSpeech + '\u0000' + normalizedDefinition)) {
			statistics.dropped("duplicate_sense");
			return;
		}
		String example = featureValueFromExamples(sense.get("SenseExample"), "example");
		sink.accept(DictionarySense.builder()
			.word(normalizedWord)
			.partOfSpeech(normalizedPartOfSpeech)
			.definition(normalizedDefinition)
			.example(blankToNull(example))
			.build());
		statistics.emitted();
	}

	private String featureValueFromExamples(JsonNode examples, String attribute) {
		if (examples == null || examples.isNull()) {
			return null;
		}
		if (examples.isArray()) {
			for (JsonNode example : examples) {
				String value = featureValue(example.path("feat"), attribute);
				if (!isBlank(value)) {
					return value;
				}
			}
			return null;
		}
		return featureValue(examples.path("feat"), attribute);
	}

	/** Finds an att/val pair in a Sense/Lemma feat object or array only. */
	private String featureValue(JsonNode features, String attribute) {
		if (features == null || features.isMissingNode() || features.isNull()) {
			return null;
		}
		if (features.isArray()) {
			for (JsonNode feature : features) {
				String value = featureValue(feature, attribute);
				if (value != null) {
					return value;
				}
			}
			return null;
		}
		if (features.isObject() && attribute.equals(features.path("att").asText(null))) {
			return features.path("val").asText(null);
		}
		return null;
	}

	private boolean isBlank(String value) {
		return value == null || value.isBlank();
	}

	private String blankToNull(String value) {
		return isBlank(value) ? null : value.trim();
	}
}
