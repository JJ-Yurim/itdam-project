package com.ssafy.c206.backend.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import com.ssafy.c206.backend.user.domain.AuthProvider;
import com.ssafy.c206.backend.user.domain.User;
import com.ssafy.c206.backend.user.domain.UserStatus;
import com.ssafy.c206.backend.user.repository.UserRepository;
import com.ssafy.c206.backend.user.service.PendingSocialRegistration;
import com.ssafy.c206.backend.user.service.SocialAccountService;
import com.ssafy.c206.backend.user.service.SocialLoginDecision.ExistingAccount;
import com.ssafy.c206.backend.user.service.SocialLoginDecision.PendingAccount;
import com.ssafy.c206.backend.user.service.SocialUserProfile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;

class SocialAccountServiceTest {

	private final UserRepository userRepository = mock(UserRepository.class);
	private final SocialAccountService service = new SocialAccountService(userRepository);

	@BeforeEach
	void setUp() {
		when(userRepository.existsByEmail(anyString())).thenReturn(false);
		when(userRepository.findByProviderAndProviderId(any(), anyString())).thenReturn(Optional.empty());
	}

	@Test
	void firstLoginReturnsPendingRegistrationWithoutPersistingUser() {
		var result = service.authenticate(new SocialUserProfile(
				AuthProvider.KAKAO, "kakao-1", "Social+tag@Gmail.com", "provider nickname"));

		assertThat(result).isEqualTo(new PendingAccount(
				new PendingSocialRegistration(AuthProvider.KAKAO, "kakao-1", "social@gmail.com")));
		verify(userRepository, never()).save(any());
	}

	@Test
	void reloginReturnsExistingAuthenticationWithoutCreatingNewAccount() {
		User existing = User.registerSocial(AuthProvider.NAVER, "naver-1", "relogin@example.com", "재로그인");
		when(userRepository.findByProviderAndProviderId(AuthProvider.NAVER, "naver-1"))
				.thenReturn(Optional.of(existing));

		var result = service.authenticate(new SocialUserProfile(
				AuthProvider.NAVER, "naver-1", "relogin@example.com", "ignored"));

		assertThat(result).isInstanceOfSatisfying(ExistingAccount.class,
				existingAccount -> assertThat(existingAccount.authentication().principal().email())
						.isEqualTo("relogin@example.com"));
		verify(userRepository, never()).save(any());
	}

	@Test
	void missingProviderIdIsRejectedBeforeAnyLookup() {
		assertThatThrownBy(() -> service.authenticate(new SocialUserProfile(
				AuthProvider.KAKAO, null, "no-id@example.com", "생계정없음")))
				.isInstanceOfSatisfying(OAuth2AuthenticationException.class,
						exception -> assertThat(exception.getError().getErrorCode())
								.isEqualTo(SocialAccountService.ERROR_PROFILE_INCOMPLETE));
		verify(userRepository, never()).save(any());
	}

	@Test
	void missingEmailIsRejectedWithConsentErrorCode() {
		assertThatThrownBy(() -> service.authenticate(new SocialUserProfile(
				AuthProvider.KAKAO, "kakao-2", null, "동의거부")))
				.isInstanceOfSatisfying(OAuth2AuthenticationException.class,
						exception -> assertThat(exception.getError().getErrorCode())
								.isEqualTo(SocialAccountService.ERROR_EMAIL_NOT_PROVIDED));
		verify(userRepository, never()).save(any());
	}

	@Test
	void emailAlreadyRegisteredByAnotherRouteIsRejectedWithoutAutoLink() {
		when(userRepository.existsByEmail("taken@example.com")).thenReturn(true);

		assertThatThrownBy(() -> service.authenticate(new SocialUserProfile(
				AuthProvider.GOOGLE, "google-1", "taken@example.com", "충돌")))
				.isInstanceOfSatisfying(OAuth2AuthenticationException.class,
						exception -> assertThat(exception.getError().getErrorCode())
								.isEqualTo(SocialAccountService.ERROR_EMAIL_ALREADY_REGISTERED));
		verify(userRepository, never()).save(any());
	}

	@Test
	void inactiveAccountCannotLogin() {
		User inactive = User.createFixture("inactive@example.com", "hash", "휴면", UserStatus.INACTIVE);
		when(userRepository.findByProviderAndProviderId(AuthProvider.KAKAO, "kakao-3"))
				.thenReturn(Optional.of(inactive));

		assertThatThrownBy(() -> service.authenticate(new SocialUserProfile(
				AuthProvider.KAKAO, "kakao-3", "inactive@example.com", "휴면")))
				.isInstanceOfSatisfying(OAuth2AuthenticationException.class,
						exception -> assertThat(exception.getError().getErrorCode())
								.isEqualTo(SocialAccountService.ERROR_ACCOUNT_INACTIVE));
	}
}
