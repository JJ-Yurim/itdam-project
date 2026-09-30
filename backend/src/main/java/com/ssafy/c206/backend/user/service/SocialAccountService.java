package com.ssafy.c206.backend.user.service;

import com.ssafy.c206.backend.common.security.AuthenticatedUser;
import com.ssafy.c206.backend.user.domain.User;
import com.ssafy.c206.backend.user.domain.UserStatus;
import com.ssafy.c206.backend.user.repository.UserRepository;
import com.ssafy.c206.backend.user.service.SessionAuthenticationService.AuthenticationResult;
import com.ssafy.c206.backend.user.service.SocialLoginDecision.ExistingAccount;
import com.ssafy.c206.backend.user.service.SocialLoginDecision.PendingAccount;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class SocialAccountService {

	public static final String ERROR_EMAIL_NOT_PROVIDED = "social_email_not_provided";
	public static final String ERROR_EMAIL_ALREADY_REGISTERED = "social_email_already_registered";
	public static final String ERROR_ACCOUNT_INACTIVE = "social_account_inactive";
	public static final String ERROR_PROFILE_INCOMPLETE = "social_profile_incomplete";

	private final UserRepository userRepository;

	@Transactional(readOnly = true)
	public SocialLoginDecision authenticate(SocialUserProfile profile) {
		if (!StringUtils.hasText(profile.providerId())) {
			throw loginFailure(ERROR_PROFILE_INCOMPLETE);
		}

		var existing = userRepository.findByProviderAndProviderId(profile.provider(), profile.providerId());
		if (existing.isPresent()) {
			return existingAccount(existing.get());
		}
		return pendingAccount(profile);
	}

	private ExistingAccount existingAccount(User user) {
		if (user.getStatus() != UserStatus.ACTIVE) {
			throw loginFailure(ERROR_ACCOUNT_INACTIVE);
		}
		return new ExistingAccount(new AuthenticationResult(
				new AuthenticatedUser(user.getId(), user.getEmail()), user.getRole()));
	}

	private PendingAccount pendingAccount(SocialUserProfile profile) {
		if (!StringUtils.hasText(profile.email())) {
			throw loginFailure(ERROR_EMAIL_NOT_PROVIDED);
		}
		String email = User.normalizeEmail(profile.email());
		if (userRepository.existsByEmail(email)) {
			throw loginFailure(ERROR_EMAIL_ALREADY_REGISTERED);
		}
		return new PendingAccount(new PendingSocialRegistration(
				profile.provider(), profile.providerId(), email));
	}

	private OAuth2AuthenticationException loginFailure(String errorCode) {
		return new OAuth2AuthenticationException(new OAuth2Error(errorCode));
	}
}
