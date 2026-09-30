package com.ssafy.c206.backend.user.service;

import com.ssafy.c206.backend.user.service.SessionAuthenticationService.AuthenticationResult;

public sealed interface SocialLoginDecision {

	record ExistingAccount(AuthenticationResult authentication) implements SocialLoginDecision {
	}

	record PendingAccount(PendingSocialRegistration registration) implements SocialLoginDecision {
	}
}
