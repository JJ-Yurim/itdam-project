package com.ssafy.c206.backend.word.etl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.fasterxml.jackson.databind.ObjectMapper;

class DictionaryEtlParserTest {

	@TempDir
	Path tempDirectory;

	private final ObjectMapper objectMapper = new ObjectMapper();
	private final DictionaryEtlParser parser = new DictionaryEtlParser(objectMapper);

	@Test
	void flattensEachSenseAndDropsInvalidRows() throws IOException {
		List<DictionarySense> output = new ArrayList<>();
		EtlStatistics statistics = parser.parse(
				getClass().getResourceAsStream("/dictionary/sense-fixture.json"), output::add);

		assertThat(output).hasSize(3);
		assertThat(output.get(0).getWord()).isEqualTo("가다");
		assertThat(output.get(0).getDefinition()).isEqualTo("한 곳에서 다른 곳으로 이동하다.");
		assertThat(output.get(0).getExample()).isEqualTo("학교에 가다.");
		assertThat(output.get(1).getExample()).isNull();
		assertThat(output.get(2).getWord()).isEqualTo("눈");
		assertThat(statistics.getEntriesSeen()).isEqualTo(3);
		assertThat(statistics.getSensesSeen()).isEqualTo(5);
		assertThat(statistics.getEmitted()).isEqualTo(3);
		assertThat(statistics.getDropped()).isEqualTo(2);
		assertThat(statistics.getDroppedByReason()).containsEntry("missing_definition", 1L);
		assertThat(statistics.getDroppedByReason()).containsEntry("duplicate_sense", 1L);
	}

	@Test
	void parserConsumesInputAsStreamAndSupportsSingleLexicalEntry() throws IOException {
		String json = "{\"LexicalResource\":{\"Lexicon\":{\"LexicalEntry\":{"
			+ "\"Lemma\":{\"feat\":{\"att\":\"writtenForm\",\"val\":\"빛\"}},"
			+ "\"feat\":{\"att\":\"partOfSpeech\",\"val\":\"명사\"},"
			+ "\"Sense\":{\"feat\":{\"att\":\"definition\",\"val\":\"빛나다\"}}}}}}";
		List<DictionarySense> output = new ArrayList<>();
		EtlStatistics statistics = parser.parse(
				new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)), output::add);

		assertThat(statistics.getEmitted()).isEqualTo(1);
		assertThat(output.get(0).getPartOfSpeech()).isEqualTo("명사");
	}

	@Test
	void malformedJsonIsReportedToCaller() {
		assertThatThrownBy(() -> parser.parse(
				new ByteArrayInputStream("{\"LexicalResource\":{".getBytes(StandardCharsets.UTF_8)),
				ignored -> { }))
			.isInstanceOf(IOException.class);
	}

	@Test
	void writesUtf8NdjsonWithoutRetainingTheWholeOutput() throws IOException {
		DictionaryEtlService service = new DictionaryEtlService(parser, objectMapper);
		ByteArrayOutputStream target = new ByteArrayOutputStream();
		EtlStatistics statistics = service.writeNdjson(
				getClass().getResourceAsStream("/dictionary/sense-fixture.json"), target);

		String ndjson = target.toString(StandardCharsets.UTF_8);
		assertThat(statistics.getEmitted()).isEqualTo(3);
		assertThat(ndjson).contains("\"word\":\"가다\"").contains("\n");
		assertThat(ndjson).doesNotContain("must not be selected");
	}

	@Test
	void processesMultipleSourcePathsSequentially() throws IOException {
		Path first = tempDirectory.resolve("first.json");
		Path second = tempDirectory.resolve("second.json");
		byte[] fixture = getClass().getResourceAsStream("/dictionary/sense-fixture.json").readAllBytes();
		Files.write(first, fixture);
		Files.write(second, fixture);
		DictionaryEtlService service = new DictionaryEtlService(parser, objectMapper);

		EtlStatistics statistics = service.writeNdjson(List.of(first, second), new ByteArrayOutputStream());

		assertThat(statistics.getEntriesSeen()).isEqualTo(6);
		assertThat(statistics.getEmitted()).isEqualTo(6);
		assertThat(statistics.getDroppedByReason()).containsEntry("duplicate_sense", 2L);
	}
}
