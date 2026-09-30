package com.ssafy.c206.backend.user;

import static com.ssafy.c206.backend.common.config.SecurityConfiguration.PENDING_SOCIAL_REGISTRATION_SESSION_ATTRIBUTE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.net.URI;
import java.util.List;
import java.util.Map;

import com.ssafy.c206.backend.common.security.AuthenticatedUser;
import com.ssafy.c206.backend.common.security.SocialOAuth2User;
import com.ssafy.c206.backend.user.domain.AuthProvider;
import com.ssafy.c206.backend.user.domain.User;
import com.ssafy.c206.backend.user.domain.UserRole;
import com.ssafy.c206.backend.user.repository.UserRepository;
import com.ssafy.c206.backend.user.service.PendingSocialRegistration;
import com.ssafy.c206.backend.user.service.SessionAuthenticationService.AuthenticationResult;
import com.ssafy.c206.backend.user.service.SocialLoginDecision.ExistingAccount;
import com.ssafy.c206.backend.user.service.SocialLoginDecision.PendingAccount;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
@ActiveProfiles("test")
class SocialLoginFlowTest {

	@Autowired WebApplicationContext webApplicationContext;
	@Autowired FilterChainProxy springSecurityFilterChain;
	@Autowired AuthenticationSuccessHandler socialLoginSuccessHandler;
	@Autowired UserRepository userRepository;
	@Autowired ClientRegistrationRepository clientRegistrationRepository;

	MockMvc mockMvc;

	@BeforeEach
	void setUpMockMvc() {
		mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
				.addFilters(springSecurityFilterChain)
				.build();
	}

	@Test
	void authorizationEndpointsRedirectToEachProvider() throws Exception {
		mockMvc.perform(get("/api/oauth2/authorization/kakao"))
				.andExpect(status().is3xxRedirection())
				.andExpect(header().string("Location", startsWith("https://kauth.kakao.com/oauth/authorize")));
		mockMvc.perform(get("/api/oauth2/authorization/naver"))
				.andExpect(status().is3xxRedirection())
				.andExpect(header().string("Location", startsWith("https://nid.naver.com/oauth2.0/authorize")));
		mockMvc.perform(get("/api/oauth2/authorization/google"))
				.andExpect(status().is3xxRedirection())
				.andExpect(header().string("Location", startsWith("https://accounts.google.com/o/oauth2")));
	}

	@Test
	void configuredRedirectUrisAreHandledByOAuth2LoginFilter() throws Exception {
		for (String registrationId : List.of("kakao", "naver", "google")) {
			ClientRegistration registration = clientRegistrationRepository.findByRegistrationId(registrationId);
			String redirectPath = URI.create(registration.getRedirectUri()).getPath();

			mockMvc.perform(get(redirectPath).param("code", "test-code").param("state", "test-state"))
					.andExpect(status().is3xxRedirection())
					.andExpect(header().string("Location", startsWith("/login?socialError=")));
		}
	}

	@Test
	void existingAccountSuccessStoresSessionAuthenticationLikePasswordLogin() throws Exception {
		AuthenticatedUser principal = new AuthenticatedUser(42L, "social@example.com");
		AuthenticationResult result = new AuthenticationResult(principal, UserRole.USER);
		SocialOAuth2User socialUser = new SocialOAuth2User(
				new ExistingAccount(result), Map.of("sub", "google-42"));
		Authentication oauthAuthentication = new UsernamePasswordAuthenticationToken(
				socialUser, null, socialUser.getAuthorities());
		MockHttpServletRequest request = new MockHttpServletRequest();
		MockHttpSession session = new MockHttpSession();
		session.setAttribute(PENDING_SOCIAL_REGISTRATION_SESSION_ATTRIBUTE,
				new PendingSocialRegistration(AuthProvider.KAKAO, "stale", "stale@example.com"));
		request.setSession(session);
		MockHttpServletResponse response = new MockHttpServletResponse();

		socialLoginSuccessHandler.onAuthenticationSuccess(request, response, oauthAuthentication);

		assertThat(response.getRedirectedUrl()).isEqualTo("/");
		SecurityContext securityContext = (SecurityContext) request.getSession(false)
				.getAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);
		assertThat(securityContext.getAuthentication()).isInstanceOf(UsernamePasswordAuthenticationToken.class);
		assertThat(securityContext.getAuthentication().getPrincipal()).isEqualTo(principal);
		assertThat(session.getAttribute(PENDING_SOCIAL_REGISTRATION_SESSION_ATTRIBUTE)).isNull();
		assertThat(response.getCookies()).anySatisfy(cookie ->
				assertThat(cookie.getName()).isEqualTo("XSRF-TOKEN"));
	}

	@Test
	void newAccountSuccessStoresPendingProfileWithoutAuthentication() throws Exception {
		PendingSocialRegistration pending = new PendingSocialRegistration(
				AuthProvider.GOOGLE, "google-new", "new@example.com");
		SocialOAuth2User socialUser = new SocialOAuth2User(
				new PendingAccount(pending), Map.of("sub", "google-new"));
		Authentication oauthAuthentication = new UsernamePasswordAuthenticationToken(
				socialUser, null, socialUser.getAuthorities());
		MockHttpServletRequest request = new MockHttpServletRequest();
		request.setSession(new MockHttpSession());
		MockHttpServletResponse response = new MockHttpServletResponse();

		socialLoginSuccessHandler.onAuthenticationSuccess(request, response, oauthAuthentication);

		assertThat(response.getRedirectedUrl()).isEqualTo("/oauth/signup");
		assertThat(request.getSession(false).getAttribute(PENDING_SOCIAL_REGISTRATION_SESSION_ATTRIBUTE))
				.isEqualTo(pending);
		assertThat(request.getSession(false)
				.getAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY)).isNull();
	}

	@Test
	@Transactional
	void socialAccountCannotLoginWithPassword() throws Exception {
		userRepository.save(User.registerSocial(
				AuthProvider.KAKAO, "kakao-pwless", "pwless@example.com", "비번없음"));

		mockMvc.perform(post("/api/v1/auth/sessions").with(csrf()).contentType("application/json")
						.content("{\"email\":\"pwless@example.com\",\"password\":\"password123\"}"))
				.andExpect(status().isUnauthorized());
	}
}
