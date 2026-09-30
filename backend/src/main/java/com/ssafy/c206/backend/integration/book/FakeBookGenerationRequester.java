package com.ssafy.c206.backend.integration.book;

import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "book.generation.client", havingValue = "fake")
public class FakeBookGenerationRequester implements BookGenerationRequester {
	private final List<BookGenerationRequestMessage> requests = new ArrayList<>();
	private volatile RuntimeException failure;

	@Override
	public synchronized void request(BookGenerationRequestMessage request) {
		if (failure != null) {
			throw failure;
		}
		requests.add(request);
	}

	public synchronized List<BookGenerationRequestMessage> requests() {
		return List.copyOf(requests);
	}

	public synchronized void clear() {
		requests.clear();
		failure = null;
	}

	public void failWith(RuntimeException exception) {
		failure = exception;
	}
}
