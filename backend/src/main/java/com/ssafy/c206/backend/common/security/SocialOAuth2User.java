package com.ssafy.c206.backend.common.security;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import com.ssafy.c206.backend.user.service.SocialLoginDecision;
import com.ssafy.c206.backend.user.service.SocialLoginDecision.ExistingAccount;
import com.ssafy.c206.backend.user.service.SocialLoginDecision.PendingAccount;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.user.OAuth2User;

public record SocialOAuth2User(
		SocialLoginDecision decision,
		Map<String, Object> attributes) implements OAuth2User {

	@Override
	public Map<String, Object> getAttributes() {
		return attributes;
	}

	@Override
	public Collection<? extends GrantedAuthority> getAuthorities() {
		if (decision instanceof ExistingAccount existing) {
			return List.of(new SimpleGrantedAuthority(existing.authentication().role().name()));
		}
		return List.of();
	}

	@Override
	public String getName() {
		if (decision instanceof ExistingAccount existing) {
			return String.valueOf(existing.authentication().principal().userId());
		}
		PendingAccount pending = (PendingAccount) decision;
		return pending.registration().provider() + ":" + pending.registration().providerId();
	}
}
