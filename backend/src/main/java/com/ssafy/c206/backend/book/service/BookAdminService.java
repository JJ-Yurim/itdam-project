package com.ssafy.c206.backend.book.service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import com.ssafy.c206.backend.book.api.AdminBookCoverResponse;
import com.ssafy.c206.backend.book.api.AdminBookCoverUpdateRequest;
import com.ssafy.c206.backend.book.api.AdminBookResponse;
import com.ssafy.c206.backend.book.api.AdminBookVisibilityUpdateRequest;
import com.ssafy.c206.backend.book.domain.Book;
import com.ssafy.c206.backend.book.domain.BookStatus;
import com.ssafy.c206.backend.book.domain.BookVisibility;
import com.ssafy.c206.backend.book.repository.BookContentRepository;
import com.ssafy.c206.backend.book.repository.BookRepository;
import com.ssafy.c206.backend.boss.image.domain.ImageAsset;
import com.ssafy.c206.backend.boss.image.service.BookCoverImageAssetService;
import com.ssafy.c206.backend.boss.image.service.BossImageUrlResolver;
import com.ssafy.c206.backend.common.api.DomainException;
import com.ssafy.c206.backend.common.api.ErrorCode;
import com.ssafy.c206.backend.common.api.PageResponse;
import com.ssafy.c206.backend.common.security.AuthenticatedUser;
import com.ssafy.c206.backend.common.security.AuthenticatedUsers;
import com.ssafy.c206.backend.common.time.ServiceClock;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class BookAdminService {
	private static final int MAX_PAGE_SIZE = 100;

	private final BookRepository bookRepository;
	private final BookContentRepository contentRepository;
	private final BookCoverImageAssetService coverAssetService;
	private final BossImageUrlResolver imageUrlResolver;
	private final ServiceClock serviceClock;

	@Transactional(readOnly = true)
	public PageResponse<AdminBookResponse> getBooks(String query, List<BookStatus> statuses,
			BookVisibility visibility, int page, int size) {
		if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
			throw new DomainException(ErrorCode.VALIDATION_ERROR, "page must be >= 0 and size must be between 1 and 100.");
		}
		List<BookStatus> requested = statuses == null ? List.of()
				: statuses.stream().filter(Objects::nonNull).distinct().toList();
		Collection<BookStatus> queryStatuses = requested.isEmpty()
				? EnumSet.allOf(BookStatus.class) : EnumSet.copyOf(requested);
		String escapedQuery = escapeLike(query);
		Page<Book> books = bookRepository.searchAdmin(escapedQuery, escapedQuery != null, !requested.isEmpty(),
				queryStatuses, visibility, visibility != null,
				PageRequest.of(page, size, Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"))));
		List<Long> bookIds = books.getContent().stream().map(Book::getId).toList();
		Map<Long, Long> contentCounts = bookIds.isEmpty() ? Collections.emptyMap()
				: contentRepository.countGroupedByBookIdIn(bookIds).stream()
						.collect(Collectors.toMap(BookContentRepository.BookContentCount::getBookId,
								BookContentRepository.BookContentCount::getContentCount));
		List<AdminBookResponse> responses = books.getContent().stream()
				.map(book -> AdminBookResponse.from(book,
						contentCounts.getOrDefault(book.getId(), 0L).intValue(), imageUrlResolver))
				.toList();
		return new PageResponse<>(responses, books.getNumber(), books.getSize(), books.getTotalElements(),
				books.getTotalPages(), books.hasNext());
	}

	@Transactional
	public AdminBookResponse updateVisibility(AuthenticatedUser principal, Long bookId,
			AdminBookVisibilityUpdateRequest request) {
		AuthenticatedUsers.requireUserId(principal);
		Book book = lockedBook(bookId, request.expectedUpdatedAt());
		Instant now = serviceClock.now();
		if (request.visibility() == BookVisibility.PUBLIC) {
			book.publish(now);
		} else {
			book.hide(now);
		}
		return AdminBookResponse.from(book,
				(int) contentRepository.countByBookId(book.getId()), imageUrlResolver);
	}

	@Transactional
	public AdminBookCoverResponse changeCover(AuthenticatedUser principal, Long bookId,
			AdminBookCoverUpdateRequest request) {
		long userId = AuthenticatedUsers.requireUserId(principal);
		Book book = bookRepository.findByIdForUpdate(bookId)
				.orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND, "Book not found: " + bookId));
		ImageAsset cover = coverAssetService.resolveAssignable(userId, request.imageAssetId());
		book.changeCover(cover, serviceClock.now());
		coverAssetService.activate(cover);
		return new AdminBookCoverResponse(book.getId(), cover.getId(), imageUrlResolver.publicUrl(cover),
				book.getUpdatedAt());
	}

	/** 173의 마이크로초 정밀도 expectedUpdatedAt 정책을 책에도 적용한다. null은 @NotNull이 걸러준다. */
	private Book lockedBook(Long bookId, Instant expectedUpdatedAt) {
		Book book = bookRepository.findByIdForUpdate(bookId)
				.orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND, "Book not found: " + bookId));
		if (!book.getUpdatedAt().truncatedTo(ChronoUnit.MICROS)
				.equals(expectedUpdatedAt.truncatedTo(ChronoUnit.MICROS))) {
			throw new DomainException(ErrorCode.CONFLICT, "Book was modified by another administrator.");
		}
		return book;
	}

	private String escapeLike(String value) {
		if (value == null || value.isBlank()) return null;
		return value.trim().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
	}
}
