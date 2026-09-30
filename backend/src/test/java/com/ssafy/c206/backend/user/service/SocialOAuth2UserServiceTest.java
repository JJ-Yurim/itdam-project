package com.ssafy.c206.backend.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;

class SocialOAuth2UserServiceTest {

	private final SocialAccountService socialAccountService = mock(SocialAccountService.class);
	private final SocialOAuth2UserService service = new SocialOAuth2UserService(socialAccountService);

	@Test
	void unsupportedRegistrationIdBecomesOAuth2FailureNotServerError() {
		assertThatThrownBy(() -> service.mapProfile("unknown-provider", Map.of()))
				.isInstanceOfSatisfying(OAuth2AuthenticationException.class,
						exception -> assertThat(exception.getError().getErrorCode())
								.isEqualTo("social_login_failed"));
	}
}
