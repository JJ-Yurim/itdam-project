package com.ssafy.c206.backend.book.reaction.service;

import java.time.Instant;

import com.ssafy.c206.backend.book.domain.Book;
import com.ssafy.c206.backend.book.domain.BookStatus;
import com.ssafy.c206.backend.book.domain.BookVisibility;
import com.ssafy.c206.backend.book.reaction.domain.BookBookmark;
import com.ssafy.c206.backend.book.reaction.domain.BookLike;
import com.ssafy.c206.backend.book.reaction.repository.BookBookmarkRepository;
import com.ssafy.c206.backend.book.reaction.repository.BookLikeRepository;
import com.ssafy.c206.backend.book.repository.BookRepository;
import com.ssafy.c206.backend.common.api.DomainException;
import com.ssafy.c206.backend.common.api.ErrorCode;
import com.ssafy.c206.backend.user.domain.User;
import com.ssafy.c206.backend.user.service.UserQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
class BookReactionTransaction {

	private final BookLikeRepository likeRepository;
	private final BookBookmarkRepository bookmarkRepository;
	private final BookRepository bookRepository;
	private final UserQueryService userQueryService;

	@Transactional
	void like(Long userId, Long bookId, Instant now) {
		User user = userQueryService.getActiveUser(userId);
		Book book = requireCompletedBook(bookId);
		if (likeRepository.existsByUserIdAndBookId(userId, bookId)) {
			return;
		}
		likeRepository.save(BookLike.create(user, book, now));
	}

	@Transactional
	void unlike(Long userId, Long bookId) {
		userQueryService.getActiveUser(userId);
		requireBook(bookId);
		likeRepository.deleteByUserIdAndBookId(userId, bookId);
	}

	@Transactional
	void bookmark(Long userId, Long bookId, Instant now) {
		User user = userQueryService.getActiveUser(userId);
		Book book = requireCompletedBook(bookId);
		if (bookmarkRepository.existsByUserIdAndBookId(userId, bookId)) {
			return;
		}
		bookmarkRepository.save(BookBookmark.create(user, book, now));
	}

	@Transactional
	void unbookmark(Long userId, Long bookId) {
		userQueryService.getActiveUser(userId);
		requireBook(bookId);
		bookmarkRepository.deleteByUserIdAndBookId(userId, bookId);
	}

	private Book requireCompletedBook(Long bookId) {
		Book book = requireBook(bookId);
		// 숨김 책은 존재를 드러내지 않도록 미완성 책과 같은 not-found 정책을 쓴다.
		if (book.getStatus() != BookStatus.COMPLETED || book.getVisibility() != BookVisibility.PUBLIC) {
			throw new DomainException(ErrorCode.NOT_FOUND, "Book not found.");
		}
		return book;
	}

	private Book requireBook(Long bookId) {
		return bookRepository.findById(bookId)
				.orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND, "Book not found."));
	}
}
