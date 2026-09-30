package com.ssafy.c206.backend.user;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import com.ssafy.c206.backend.integration.oauth.SocialUserProfiles;
import com.ssafy.c206.backend.user.domain.AuthProvider;
import com.ssafy.c206.backend.user.service.SocialUserProfile;
import org.junit.jupiter.api.Test;

class SocialUserProfilesTest {

	@Test
	void mapsKakaoAttributes() {
		SocialUserProfile profile = SocialUserProfiles.from("kakao", Map.of(
				"id", 123456789L,
				"kakao_account", Map.of(
						"email", "kakao-user@example.com",
						"profile", Map.of("nickname", "카카오사용자"))));

		assertThat(profile.provider()).isEqualTo(AuthProvider.KAKAO);
		assertThat(profile.providerId()).isEqualTo("123456789");
		assertThat(profile.email()).isEqualTo("kakao-user@example.com");
		assertThat(profile.nickname()).isEqualTo("카카오사용자");
	}

	@Test
	void kakaoEmailIsNullWhenConsentDeclined() {
		// account_email은 선택 동의라 kakao_account에 email이 없을 수 있다.
		SocialUserProfile profile = SocialUserProfiles.from("kakao", Map.of(
				"id", 123456789L,
				"kakao_account", Map.of("profile", Map.of("nickname", "동의거부"))));

		assertThat(profile.email()).isNull();
		assertThat(profile.providerId()).isEqualTo("123456789");
	}

	@Test
	void missingIdBecomesNullNotStringLiteral() {
		// id 누락 시 "null" 문자열이 아닌 null이어야 계정 오매칭·저장이 원천 차단된다.
		SocialUserProfile profile = SocialUserProfiles.from("kakao", Map.of(
				"kakao_account", Map.of("email", "no-id@example.com")));

		assertThat(profile.providerId()).isNull();
	}

	@Test
	void mapsNaverAttributesWrappedInResponse() {
		SocialUserProfile profile = SocialUserProfiles.from("naver", Map.of(
				"resultcode", "00",
				"response", Map.of(
						"id", "naver-abc-123",
						"email", "naver-user@example.com",
						"nickname", "네이버사용자")));

		assertThat(profile.provider()).isEqualTo(AuthProvider.NAVER);
		assertThat(profile.providerId()).isEqualTo("naver-abc-123");
		assertThat(profile.email()).isEqualTo("naver-user@example.com");
		assertThat(profile.nickname()).isEqualTo("네이버사용자");
	}

	@Test
	void mapsGoogleAttributes() {
		SocialUserProfile profile = SocialUserProfiles.from("google", Map.of(
				"sub", "google-sub-42",
				"email", "google-user@example.com",
				"name", "구글사용자"));

		assertThat(profile.provider()).isEqualTo(AuthProvider.GOOGLE);
		assertThat(profile.providerId()).isEqualTo("google-sub-42");
		assertThat(profile.email()).isEqualTo("google-user@example.com");
		assertThat(profile.nickname()).isEqualTo("구글사용자");
	}
}
