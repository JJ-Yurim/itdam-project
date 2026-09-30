package com.ssafy.c206.backend.word.etl;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;

/** Small adapter for writing the streaming output as UTF-8 NDJSON. */
@RequiredArgsConstructor
public class DictionaryEtlService {

	private final DictionaryEtlParser parser;
	private final ObjectMapper objectMapper;

	public EtlStatistics writeNdjson(InputStream source, OutputStream target) throws IOException {
		Objects.requireNonNull(target, "target");
		var writer = objectMapper.writer();
		return parser.parse(source, sense -> {
			try {
				target.write(writer.writeValueAsBytes(sense));
				target.write('\n');
			} catch (IOException exception) {
				throw new DictionaryEtlWriteException(exception);
			}
		});
	}

	/** Processes multiple source files one at a time without retaining their contents. */
	public EtlStatistics writeNdjson(Iterable<Path> sources, OutputStream target) throws IOException {
		Objects.requireNonNull(sources, "sources");
		EtlStatistics total = new EtlStatistics();
		for (Path source : sources) {
			try (InputStream input = Files.newInputStream(source)) {
				total.merge(writeNdjson(input, target));
			}
		}
		return total;
	}

	public static class DictionaryEtlWriteException extends RuntimeException {
		public DictionaryEtlWriteException(IOException cause) {
			super(cause);
		}
	}
}
