package com.ssafy.c206.backend.user;

import static com.ssafy.c206.backend.common.config.SecurityConfiguration.PENDING_SOCIAL_REGISTRATION_SESSION_ATTRIBUTE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.ZoneId;

import com.ssafy.c206.backend.user.domain.AuthProvider;
import com.ssafy.c206.backend.user.domain.User;
import com.ssafy.c206.backend.user.repository.UserRepository;
import com.ssafy.c206.backend.user.service.PendingSocialRegistration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
@ActiveProfiles("test")
class SocialRegistrationApiTest {

	private static final long CURRENT_AGREEMENT_ID = 1L;
	private static final ZoneId SERVICE_ZONE = ZoneId.of("Asia/Seoul");

	@Autowired WebApplicationContext webApplicationContext;
	@Autowired FilterChainProxy springSecurityFilterChain;
	@Autowired UserRepository userRepository;

	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
				.addFilters(springSecurityFilterChain)
				.build();
	}

	@Test
	void pendingStatusExposesOnlyBoolean() throws Exception {
		MockHttpSession session = pendingSession("pending-status");

		mockMvc.perform(get("/api/v1/auth/social-registration").session(session))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.pending").value(true))
				.andExpect(jsonPath("$.email").doesNotExist())
				.andExpect(jsonPath("$.providerId").doesNotExist());

		mockMvc.perform(get("/api/v1/auth/social-registration"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.pending").value(false));
	}

	@Test
	void completionRequiresPendingOAuthSession() throws Exception {
		mockMvc.perform(post("/api/v1/auth/social-registration").with(csrf())
						.contentType("application/json")
						.content(validBody("no-session")))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
	}

	@Test
	void completionPersistsChosenNicknameAndAgreementThenAuthenticates() throws Exception {
		String suffix = String.valueOf(System.nanoTime());
		MockHttpSession session = pendingSession(suffix);

		mockMvc.perform(post("/api/v1/auth/social-registration").session(session).with(csrf())
						.contentType("application/json")
						.content(validBody("직접선택-" + suffix)))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.nickname").value("직접선택-" + suffix));

		User saved = userRepository.findByEmail("social-" + suffix + "@example.com").orElseThrow();
		assertThat(saved.getProvider()).isEqualTo(AuthProvider.GOOGLE);
		assertThat(saved.getProviderId()).isEqualTo("provider-" + suffix);
		assertThat(saved.getPasswordHash()).isNull();
		assertThat(saved.getCopyrightAgreement().getId()).isEqualTo(CURRENT_AGREEMENT_ID);
		assertThat(saved.getCopyrightAgreedAt()).isNotNull();
		assertThat(saved.getCopyrightAgreementExpiresAt()).isEqualTo(
				saved.getCopyrightAgreedAt().atZone(SERVICE_ZONE).plusMonths(12).toInstant());
		assertThat(session.getAttribute(PENDING_SOCIAL_REGISTRATION_SESSION_ATTRIBUTE)).isNull();
		SecurityContext context = (SecurityContext) session.getAttribute(
				HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);
		assertThat(context.getAuthentication().getPrincipal()).isNotNull();
	}

	@Test
	void completionRejectsMissingConsentAndKeepsPendingSession() throws Exception {
		String suffix = String.valueOf(System.nanoTime());
		MockHttpSession session = pendingSession(suffix);
		String body = "{\"nickname\":\"동의없음-" + suffix + "\","
				+ "\"copyrightAgreementId\":1,\"copyrightAgreed\":false}";

		mockMvc.perform(post("/api/v1/auth/social-registration").session(session).with(csrf())
						.contentType("application/json").content(body))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
		assertThat(session.getAttribute(PENDING_SOCIAL_REGISTRATION_SESSION_ATTRIBUTE)).isNotNull();
	}

	@Test
	void completionRejectsDuplicateNicknameAndKeepsPendingSession() throws Exception {
		String suffix = String.valueOf(System.nanoTime());
		String nickname = "중복닉네임-" + suffix;
		userRepository.saveAndFlush(User.createFixture(
				"existing-" + suffix + "@example.com", "hash", nickname));
		MockHttpSession session = pendingSession(suffix);

		mockMvc.perform(post("/api/v1/auth/social-registration").session(session).with(csrf())
						.contentType("application/json").content(validBody(nickname)))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("CONFLICT"));
		assertThat(session.getAttribute(PENDING_SOCIAL_REGISTRATION_SESSION_ATTRIBUTE)).isNotNull();
	}

	@Test
	void completionRejectsStaleAgreement() throws Exception {
		String suffix = String.valueOf(System.nanoTime());
		MockHttpSession session = pendingSession(suffix);
		String body = "{\"nickname\":\"약관변경-" + suffix + "\","
				+ "\"copyrightAgreementId\":9999,\"copyrightAgreed\":true}";

		mockMvc.perform(post("/api/v1/auth/social-registration").session(session).with(csrf())
						.contentType("application/json").content(body))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("COPYRIGHT_AGREEMENT_MISMATCH"));
	}

	@Test
	void completedPendingSessionCannotBeUsedTwice() throws Exception {
		String suffix = String.valueOf(System.nanoTime());
		MockHttpSession session = pendingSession(suffix);

		mockMvc.perform(post("/api/v1/auth/social-registration").session(session).with(csrf())
						.contentType("application/json").content(validBody("한번만-" + suffix)))
				.andExpect(status().isCreated());
		mockMvc.perform(post("/api/v1/auth/social-registration").session(session).with(csrf())
						.contentType("application/json").content(validBody("두번째-" + suffix)))
				.andExpect(status().isUnauthorized());
	}

	private MockHttpSession pendingSession(String suffix) {
		MockHttpSession session = new MockHttpSession();
		session.setAttribute(PENDING_SOCIAL_REGISTRATION_SESSION_ATTRIBUTE,
				new PendingSocialRegistration(AuthProvider.GOOGLE, "provider-" + suffix,
						"social-" + suffix + "@example.com"));
		return session;
	}

	private String validBody(String nickname) {
		return "{\"nickname\":\"" + nickname + "\","
				+ "\"copyrightAgreementId\":" + CURRENT_AGREEMENT_ID + ",\"copyrightAgreed\":true}";
	}
}
