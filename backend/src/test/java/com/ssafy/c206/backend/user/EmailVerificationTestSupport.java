package com.ssafy.c206.backend.user;

import java.time.Duration;
import java.util.UUID;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ssafy.c206.backend.user.api.SignupRequest;
import com.ssafy.c206.backend.user.domain.User;
import com.ssafy.c206.backend.user.service.EmailVerificationStore;

/** 가입에는 이메일 인증 선행이 필수라, 인증 API를 거치지 않는 테스트가 표식과 검증 토큰을 직접 심을 때 쓴다. */
public final class EmailVerificationTestSupport {

	public static final long CURRENT_COPYRIGHT_AGREEMENT_ID = 1L;

	private static final ObjectMapper MAPPER = new ObjectMapper();

	private EmailVerificationTestSupport() {
	}

	/** 인증 표식을 심고 가입 요청에 제출할 검증 토큰을 반환한다. 표식 키는 프로덕션과 같은 정규화 규칙을 쓴다. */
	public static String markVerified(EmailVerificationStore store, String email) {
		String token = UUID.randomUUID().toString();
		store.markVerified(User.normalizeEmail(email), token, Duration.ofMinutes(10));
		return token;
	}

	/** 인증 표식 선행이 포함된 가입 JSON 본문을 만든다. */
	public static String verifiedSignupBody(EmailVerificationStore store, String email, String password,
			String nickname) {
		try {
			return MAPPER.writeValueAsString(
					new SignupRequest(email, password, nickname, markVerified(store, email),
							CURRENT_COPYRIGHT_AGREEMENT_ID, true));
		} catch (JsonProcessingException exception) {
			throw new IllegalStateException(exception);
		}
	}
}
