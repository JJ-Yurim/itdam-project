package com.ssafy.c206.backend.user.service;

import java.util.Map;

import com.ssafy.c206.backend.common.security.SocialOAuth2User;
import com.ssafy.c206.backend.integration.oauth.SocialUserProfiles;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SocialOAuth2UserService extends DefaultOAuth2UserService {

	private final SocialAccountService socialAccountService;

	@Override
	public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
		OAuth2User oauth2User = super.loadUser(userRequest);
		SocialUserProfile profile = mapProfile(
				userRequest.getClientRegistration().getRegistrationId(), oauth2User.getAttributes());
		SocialLoginDecision decision = socialAccountService.authenticate(profile);
		return new SocialOAuth2User(decision, oauth2User.getAttributes());
	}

	SocialUserProfile mapProfile(String registrationId, Map<String, Object> attributes) {
		try {
			return SocialUserProfiles.from(registrationId, attributes);
		} catch (RuntimeException exception) {
			throw new OAuth2AuthenticationException(new OAuth2Error("social_login_failed"),
					"Unsupported social login profile: " + registrationId, exception);
		}
	}
}
