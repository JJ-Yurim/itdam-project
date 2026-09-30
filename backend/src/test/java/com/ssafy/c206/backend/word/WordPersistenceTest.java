package com.ssafy.c206.backend.word;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import com.ssafy.c206.backend.word.domain.Word;
import com.ssafy.c206.backend.word.etl.DictionarySense;
import com.ssafy.c206.backend.word.repository.WordRepository;
import com.ssafy.c206.backend.word.service.ActiveWordQueryService;
import com.ssafy.c206.backend.word.service.WordImportService;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class WordPersistenceTest {

	@Autowired
	private WordRepository wordRepository;

	@Autowired
	private ActiveWordQueryService activeWordQueryService;

	@Autowired
	private WordImportService wordImportService;

	@Autowired
	private Validator validator;

	@BeforeEach
	void cleanWords() {
		wordRepository.deleteAll();
	}

	@Test
	void preservesPolysemyWithoutWordUniqueConstraint() {
		wordRepository.save(Word.from(DictionarySense.builder()
				.word("배")
				.partOfSpeech("명사")
				.definition("먹는 과일")
				.build()));
		wordRepository.save(Word.from(DictionarySense.builder()
				.word("배")
				.partOfSpeech("명사")
				.definition("사람이나 물건을 싣는 탈것")
				.build()));

		assertThat(wordRepository.count()).isEqualTo(2);
		assertThat(activeWordQueryService.count()).isEqualTo(2);
	}

	@Test
	void importsOnlyValidSenseArtifactsAndKeepsExampleNullable() throws Exception {
		String ndjson = """
				{"word":"가다","partOfSpeech":"동사","definition":"한 곳에서 다른 곳으로 이동하다"}
				{"word":"오다","partOfSpeech":"동사","definition":"이쪽으로 이동하다","example":"집에 오다"}
				{"word":"보다","partOfSpeech":"동사","definition":"눈으로 감지하다"}
				{"word":"읽다","partOfSpeech":"동사","definition":"글을 소리 내거나 눈으로 이해하다"}
				{"word":"쓰다","partOfSpeech":"동사","definition":"글자를 적다"}
				{"word":"","partOfSpeech":"동사","definition":"잘못된 표제어"}
				{malformed}
				""";

		WordImportService.ImportSummary summary = wordImportService.importNdjson(
				new ByteArrayInputStream(ndjson.getBytes(StandardCharsets.UTF_8)));

		assertThat(summary.imported()).isEqualTo(5);
		assertThat(summary.rejected()).isEqualTo(2);
		assertThat(activeWordQueryService.count()).isEqualTo(5);
		assertThat(activeWordQueryService.findAll()).extracting(Word::getExample).contains(null, "집에 오다");
	}

	@Test
	void enforcesWordValidationAndDefaultsActive() {
		Word invalid = Word.from(DictionarySense.builder().word("").partOfSpeech("명사").definition("").build());

		assertThat(validator.validate(invalid)).isNotEmpty();
		Word valid = Word.from(DictionarySense.builder().word("하늘").partOfSpeech("명사")
				.definition("지구를 둘러싼 무한히 넓은 공간").build());
		assertThat(valid.isActive()).isTrue();
	}
}
