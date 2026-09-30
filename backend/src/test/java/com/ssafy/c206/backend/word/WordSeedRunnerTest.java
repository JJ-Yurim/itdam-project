package com.ssafy.c206.backend.word;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import com.ssafy.c206.backend.word.repository.WordRepository;
import com.ssafy.c206.backend.word.service.WordImportService;
import com.ssafy.c206.backend.word.service.WordSeedRunner;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

// 멱등 가드가 "활성 단어 0건"에 의존하므로, 다른 테스트가 단어를 적재하는
// 공유 H2와 분리된 전용 인메모리 DB에서 검증한다.
@SpringBootTest(properties =
		"spring.datasource.url=jdbc:h2:mem:wordseed;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE")
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class WordSeedRunnerTest {

	@Autowired private WordImportService wordImportService;
	@Autowired private WordRepository wordRepository;

	@TempDir
	Path tempDir;

	@Test
	void seedsOnceAndSecondRunIsIdempotent() throws Exception {
		Path seed = tempDir.resolve("words.ndjson");
		Files.writeString(seed, String.join("\n",
				"{\"word\":\"시드단어일\",\"partOfSpeech\":\"명사\",\"definition\":\"첫 번째 시드 단어.\",\"example\":\"시드단어일을 사용했다.\"}",
				"{\"word\":\"시드단어이\",\"partOfSpeech\":\"명사\",\"definition\":\"두 번째 시드 단어.\",\"example\":null}",
				"invalid json line"), StandardCharsets.UTF_8);
		WordSeedRunner runner = new WordSeedRunner(wordImportService, wordRepository, seed.toString());

		runner.run(null);
		long afterFirst = wordRepository.countByIsActiveTrue();
		assertThat(afterFirst).isEqualTo(2);

		runner.run(null);
		assertThat(wordRepository.countByIsActiveTrue()).isEqualTo(afterFirst);
	}

	@Test
	void missingSeedFileDoesNotFailStartup() {
		WordSeedRunner runner = new WordSeedRunner(wordImportService, wordRepository,
				tempDir.resolve("absent.ndjson").toString());

		assertThatCode(() -> runner.run(null)).doesNotThrowAnyException();
	}
}
