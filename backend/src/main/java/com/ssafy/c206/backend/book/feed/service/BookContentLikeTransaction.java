package com.ssafy.c206.backend.book.feed.service;

import java.time.Instant;

import com.ssafy.c206.backend.book.domain.BookContent;
import com.ssafy.c206.backend.book.domain.BookStatus;
import com.ssafy.c206.backend.book.domain.BookVisibility;
import com.ssafy.c206.backend.book.feed.domain.BookContentLike;
import com.ssafy.c206.backend.book.feed.repository.BookContentLikeRepository;
import com.ssafy.c206.backend.book.repository.BookContentRepository;
import com.ssafy.c206.backend.common.api.DomainException;
import com.ssafy.c206.backend.common.api.ErrorCode;
import com.ssafy.c206.backend.user.domain.User;
import com.ssafy.c206.backend.user.service.UserQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
class BookContentLikeTransaction {

	private final BookContentLikeRepository likeRepository;
	private final BookContentRepository bookContentRepository;
	private final UserQueryService userQueryService;

	@Transactional
	void like(Long userId, Long bookContentId, Instant now) {
		User user = userQueryService.getActiveUser(userId);
		BookContent content = requireCompletedBookContent(bookContentId);
		if (likeRepository.existsByUserIdAndBookContentId(userId, bookContentId)) {
			return;
		}
		likeRepository.save(BookContentLike.create(user, content, now));
	}

	@Transactional
	void unlike(Long userId, Long bookContentId) {
		userQueryService.getActiveUser(userId);
		if (!bookContentRepository.existsById(bookContentId)) {
			throw new DomainException(ErrorCode.NOT_FOUND, "Book content not found.");
		}
		likeRepository.deleteByUserIdAndBookContentId(userId, bookContentId);
	}

	private BookContent requireCompletedBookContent(Long bookContentId) {
		BookContent content = bookContentRepository.findById(bookContentId)
				.orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND, "Book content not found."));
		// 숨김 책의 문장도 존재를 드러내지 않도록 같은 not-found 정책을 쓴다.
		if (content.getBook().getStatus() != BookStatus.COMPLETED
				|| content.getBook().getVisibility() != BookVisibility.PUBLIC) {
			throw new DomainException(ErrorCode.NOT_FOUND, "Book content not found.");
		}
		return content;
	}
}
