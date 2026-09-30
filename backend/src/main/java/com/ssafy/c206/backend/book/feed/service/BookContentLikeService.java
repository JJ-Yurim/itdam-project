package com.ssafy.c206.backend.book.feed.service;

import com.ssafy.c206.backend.common.security.AuthenticatedUser;
import com.ssafy.c206.backend.common.security.AuthenticatedUsers;
import com.ssafy.c206.backend.common.time.ServiceClock;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

/**
 * 문장(책 콘텐츠) 좋아요 등록·해제. 두 요청 모두 멱등하다 — 이미 좋아요한 상태의 등록과
 * 좋아요가 없는 상태의 해제는 모두 성공으로 끝난다.
 */
@Service
@RequiredArgsConstructor
public class BookContentLikeService {

	private final BookContentLikeTransaction transaction;
	private final ServiceClock serviceClock;

	public void like(AuthenticatedUser principal, Long bookContentId) {
		long userId = AuthenticatedUsers.requireUserId(principal);
		try {
			transaction.like(userId, bookContentId, serviceClock.now());
		} catch (DataIntegrityViolationException exception) {
			// 동시 중복 좋아요는 유니크 제약이 한쪽을 거른다. 멱등 성공으로 처리한다.
		}
	}

	public void unlike(AuthenticatedUser principal, Long bookContentId) {
		long userId = AuthenticatedUsers.requireUserId(principal);
		transaction.unlike(userId, bookContentId);
	}
}
