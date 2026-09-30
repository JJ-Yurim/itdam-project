package com.ssafy.c206.backend.word.service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ssafy.c206.backend.word.domain.Word;
import com.ssafy.c206.backend.word.etl.DictionarySense;
import com.ssafy.c206.backend.word.repository.WordRepository;
import jakarta.persistence.EntityManager;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/** Streams DATA-001 NDJSON and commits bounded batches. */
@Service
@RequiredArgsConstructor
public class WordImportService {

	private static final int BATCH_SIZE = 500;

	private final ObjectMapper objectMapper;
	private final WordRepository wordRepository;
	private final Validator validator;
	private final TransactionTemplate transactionTemplate;
	private final EntityManager entityManager;

	public ImportSummary importNdjson(InputStream input) throws IOException {
		long imported = 0;
		long rejected = 0;
		List<Word> batch = new ArrayList<>(BATCH_SIZE);
		try (BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
			String line;
			while ((line = reader.readLine()) != null) {
				if (line.isBlank()) continue;
				try {
					DictionarySense sense = objectMapper.readValue(line, DictionarySense.class);
					Word word = Word.from(sense);
					if (validator.validate(word).isEmpty()) {
						batch.add(word);
						imported++;
					} else {
						rejected++;
					}
				} catch (IOException | RuntimeException exception) {
					rejected++;
				}
				if (batch.size() >= BATCH_SIZE) {
					commitBatch(batch);
					batch.clear();
				}
			}
		}
		if (!batch.isEmpty()) commitBatch(batch);
		return new ImportSummary(imported, rejected);
	}

	private void commitBatch(List<Word> batch) {
		List<Word> snapshot = List.copyOf(batch);
		transactionTemplate.executeWithoutResult(status -> {
			wordRepository.saveAll(snapshot);
			wordRepository.flush();
			entityManager.clear();
		});
	}

	public record ImportSummary(long imported, long rejected) { }
}
