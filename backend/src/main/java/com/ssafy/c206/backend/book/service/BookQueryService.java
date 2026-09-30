package com.ssafy.c206.backend.book.service;

import java.util.Collection;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.ssafy.c206.backend.book.domain.Book;
import com.ssafy.c206.backend.book.domain.BookStatus;
import com.ssafy.c206.backend.book.repository.BookRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class BookQueryService {

	private final BookRepository bookRepository;

	@Transactional(readOnly = true)
	public Map<Long, BookSummary> findSummariesByBossRunIds(Collection<Long> bossRunIds) {
		if (bossRunIds.isEmpty()) {
			return Map.of();
		}
		return bookRepository.findByBossRunIdIn(bossRunIds).stream()
				.map(BookSummary::from)
				.collect(Collectors.toMap(BookSummary::bossRunId, Function.identity()));
	}

	public record BookSummary(Long bossRunId, Long bookId, BookStatus status) {
		private static BookSummary from(Book book) {
			return new BookSummary(book.getBossRun().getId(), book.getId(), book.getStatus());
		}
	}
}
