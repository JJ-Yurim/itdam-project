package com.ssafy.c206.backend.user.domain;

import java.util.Locale;

/** 계정이 만들어진 인증 경로. LOCAL은 이메일+비밀번호, 나머지는 소셜 로그인이다. */
public enum AuthProvider {
	LOCAL, KAKAO, NAVER, GOOGLE;

	/** OAuth2 registrationId(kakao/naver/google)를 enum으로 변환한다. */
	public static AuthProvider fromRegistrationId(String registrationId) {
		return valueOf(registrationId.toUpperCase(Locale.ROOT));
	}
}
