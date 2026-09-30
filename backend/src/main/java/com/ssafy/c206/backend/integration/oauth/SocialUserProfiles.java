package com.ssafy.c206.backend.integration.oauth;

import java.util.Map;

import com.ssafy.c206.backend.user.domain.AuthProvider;
import com.ssafy.c206.backend.user.service.SocialUserProfile;

/** provider별 사용자 정보 응답 구조 차이를 한 곳에서 흡수한다. */
public final class SocialUserProfiles {

	private SocialUserProfiles() {
	}

	public static SocialUserProfile from(String registrationId, Map<String, Object> attributes) {
		AuthProvider provider = AuthProvider.fromRegistrationId(registrationId);
		return switch (provider) {
			case KAKAO -> fromKakao(attributes);
			case NAVER -> fromNaver(attributes);
			case GOOGLE -> fromGoogle(attributes);
			case LOCAL -> throw new IllegalArgumentException("LOCAL is not a social provider");
		};
	}

	// https://developers.kakao.com/docs/latest/ko/kakaologin/rest-api#req-user-info
	// 이메일은 선택 동의(account_email)라 kakao_account에 없을 수 있다.
	private static SocialUserProfile fromKakao(Map<String, Object> attributes) {
		Map<String, Object> account = subMap(attributes, "kakao_account");
		Map<String, Object> profile = subMap(account, "profile");
		return new SocialUserProfile(
				AuthProvider.KAKAO,
				stringOrNull(attributes, "id"),
				stringOrNull(account, "email"),
				stringOrNull(profile, "nickname"));
	}

	// https://developers.naver.com/docs/login/profile/ — 본문이 response 객체로 감싸여 온다.
	private static SocialUserProfile fromNaver(Map<String, Object> attributes) {
		Map<String, Object> response = subMap(attributes, "response");
		return new SocialUserProfile(
				AuthProvider.NAVER,
				stringOrNull(response, "id"),
				stringOrNull(response, "email"),
				stringOrNull(response, "nickname"));
	}

	private static SocialUserProfile fromGoogle(Map<String, Object> attributes) {
		return new SocialUserProfile(
				AuthProvider.GOOGLE,
				stringOrNull(attributes, "sub"),
				stringOrNull(attributes, "email"),
				stringOrNull(attributes, "name"));
	}

	@SuppressWarnings("unchecked")
	private static Map<String, Object> subMap(Map<String, Object> attributes, String key) {
		Object value = attributes.get(key);
		return value instanceof Map ? (Map<String, Object>) value : Map.of();
	}

	private static String stringOrNull(Map<String, Object> attributes, String key) {
		Object value = attributes.get(key);
		return value == null ? null : String.valueOf(value);
	}
}
