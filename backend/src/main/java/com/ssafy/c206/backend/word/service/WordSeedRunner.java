package com.ssafy.c206.backend.word.service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import com.ssafy.c206.backend.word.repository.WordRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * 기동 시 서버에 배치된 NDJSON 시드 파일로 단어 사전을 1회 적재한다.
 * Word는 다의어 허용으로 단어명 유니크 제약이 없으므로,
 * 활성 단어가 하나라도 있으면 실행하지 않는 멱등 가드를 둔다.
 */
@Component
@Slf4j
public class WordSeedRunner implements ApplicationRunner {

	private final WordImportService wordImportService;
	private final WordRepository wordRepository;
	private final Path seedPath;

	public WordSeedRunner(WordImportService wordImportService, WordRepository wordRepository,
			@Value("${word.seed.path:/app/data/seed/words.ndjson}") String seedPath) {
		this.wordImportService = wordImportService;
		this.wordRepository = wordRepository;
		this.seedPath = Path.of(seedPath);
	}

	@Override
	public void run(ApplicationArguments arguments) {
		if (wordRepository.countByIsActiveTrue() > 0) {
			log.info("Word seed skipped: active words already exist.");
			return;
		}
		if (!Files.isRegularFile(seedPath)) {
			log.info("Word seed skipped: seed file not found at {}.", seedPath);
			return;
		}
		try (InputStream input = Files.newInputStream(seedPath)) {
			WordImportService.ImportSummary summary = wordImportService.importNdjson(input);
			log.info("Word seed finished from {}: {} imported, {} rejected.",
					seedPath, summary.imported(), summary.rejected());
		} catch (IOException exception) {
			// 시딩 실패가 서비스 기동을 막지 않도록 한다. 운영자가 파일을 고친 뒤 재기동하면 재시도된다.
			log.error("Word seed failed reading {}.", seedPath, exception);
		}
	}
}
