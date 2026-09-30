package com.ssafy.c206.backend.book.service;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.ssafy.c206.backend.book.api.MyContributedBookResponse;
import com.ssafy.c206.backend.book.domain.Book;
import com.ssafy.c206.backend.book.domain.BookStatus;
import com.ssafy.c206.backend.book.domain.BookVisibility;
import com.ssafy.c206.backend.book.repository.BookContentRepository;
import com.ssafy.c206.backend.book.repository.BookRepository;
import com.ssafy.c206.backend.boss.image.service.BossImageUrlResolver;
import com.ssafy.c206.backend.common.api.DomainException;
import com.ssafy.c206.backend.common.api.ErrorCode;
import com.ssafy.c206.backend.common.api.PageResponse;
import com.ssafy.c206.backend.common.security.AuthenticatedUser;
import com.ssafy.c206.backend.common.security.AuthenticatedUsers;
import com.ssafy.c206.backend.user.service.UserQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MyLibraryQueryService {

	private final BookContentRepository contentRepository;
	private final BookRepository bookRepository;
	private final UserQueryService userQueryService;
	private final BossImageUrlResolver imageUrlResolver;

	@Transactional(readOnly = true)
	public PageResponse<MyContributedBookResponse> getMyContributedBooks(AuthenticatedUser principal, int page, int size) {
		long userId = AuthenticatedUsers.requireUserId(principal);
		userQueryService.getActiveUser(userId);

		var rows = contentRepository.findContributedCompletedBooks(userId, BookStatus.COMPLETED,
				BookVisibility.PUBLIC, PageRequest.of(page, size));
		if (rows.isEmpty()) {
			return new PageResponse<>(List.of(), rows.getNumber(), rows.getSize(), rows.getTotalElements(),
					rows.getTotalPages(), rows.hasNext());
		}
		List<Long> bookIds = rows.getContent().stream()
				.map(BookContentRepository.ContributedBookRow::getBookId).toList();
		Map<Long, Book> booksById = bookRepository.findDetailsByIdIn(bookIds).stream()
				.collect(Collectors.toMap(Book::getId, Function.identity()));
		Map<Long, Long> contentCounts = contentRepository.countGroupedByBookIdIn(bookIds).stream()
				.collect(Collectors.toMap(BookContentRepository.BookContentCount::getBookId,
						BookContentRepository.BookContentCount::getContentCount));

		List<MyContributedBookResponse> responses = rows.getContent().stream().map(row -> {
			Book book = requireBook(booksById, row.getBookId());
			String coverUrl = imageUrlResolver.publicUrlOrNull(book.getCoverImageAsset());
			return MyContributedBookResponse.from(book, coverUrl,
					contentCounts.getOrDefault(book.getId(), 0L).intValue(),
					row.getMyIncludedContentCount().intValue());
		}).toList();
		return new PageResponse<>(responses, rows.getNumber(), rows.getSize(), rows.getTotalElements(),
				rows.getTotalPages(), rows.hasNext());
	}

	private Book requireBook(Map<Long, Book> booksById, Long bookId) {
		Book book = booksById.get(bookId);
		if (book == null) {
			throw new DomainException(ErrorCode.INTERNAL_ERROR, "Contributed book details are missing.");
		}
		return book;
	}
}
