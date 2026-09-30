package com.ssafy.c206.backend.book.reaction.service;

import com.ssafy.c206.backend.common.security.AuthenticatedUser;
import com.ssafy.c206.backend.common.security.AuthenticatedUsers;
import com.ssafy.c206.backend.common.time.ServiceClock;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

/**
 * 책 좋아요·북마크 등록·해제. 모든 요청은 멱등하다 — 이미 등록된 상태의 재등록과
 * 등록이 없는 상태의 해제는 모두 성공으로 끝나고, 동시 중복 등록은 유니크 제약
 * 위반을 잡아 성공으로 수습한다.
 */
@Service
@RequiredArgsConstructor
public class BookReactionService {

	private final BookReactionTransaction transaction;
	private final ServiceClock serviceClock;

	public void like(AuthenticatedUser principal, Long bookId) {
		long userId = AuthenticatedUsers.requireUserId(principal);
		try {
			transaction.like(userId, bookId, serviceClock.now());
		} catch (DataIntegrityViolationException exception) {
			// 동시 중복 등록은 멱등 성공으로 처리한다.
		}
	}

	public void unlike(AuthenticatedUser principal, Long bookId) {
		long userId = AuthenticatedUsers.requireUserId(principal);
		transaction.unlike(userId, bookId);
	}

	public void bookmark(AuthenticatedUser principal, Long bookId) {
		long userId = AuthenticatedUsers.requireUserId(principal);
		try {
			transaction.bookmark(userId, bookId, serviceClock.now());
		} catch (DataIntegrityViolationException exception) {
			// 동시 중복 등록은 멱등 성공으로 처리한다.
		}
	}

	public void unbookmark(AuthenticatedUser principal, Long bookId) {
		long userId = AuthenticatedUsers.requireUserId(principal);
		transaction.unbookmark(userId, bookId);
	}
}
