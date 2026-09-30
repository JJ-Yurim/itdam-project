package com.ssafy.c206.backend.common.security;

import com.ssafy.c206.backend.common.api.DomainException;
import com.ssafy.c206.backend.common.api.ErrorCode;

/** 서비스 진입 지점에서 인증 주체를 검증하는 공통 가드. */
public final class AuthenticatedUsers {

	private AuthenticatedUsers() {
	}

	/** 인증 주체가 없으면 UNAUTHORIZED로 거부하고 userId를 반환한다. */
	public static long requireUserId(AuthenticatedUser principal) {
		if (principal == null || principal.userId() == null) {
			throw new DomainException(ErrorCode.UNAUTHORIZED);
		}
		return principal.userId();
	}
}
