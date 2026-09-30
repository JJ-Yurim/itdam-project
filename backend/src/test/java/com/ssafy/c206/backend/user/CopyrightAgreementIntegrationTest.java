package com.ssafy.c206.backend.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.ZoneId;

import com.ssafy.c206.backend.user.repository.UserRepository;
import com.ssafy.c206.backend.user.service.EmailVerificationStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
@ActiveProfiles("test")
class CopyrightAgreementIntegrationTest {

	private static final ZoneId SERVICE_ZONE = ZoneId.of("Asia/Seoul");

	@Autowired WebApplicationContext webApplicationContext;
	@Autowired FilterChainProxy springSecurityFilterChain;
	@Autowired EmailVerificationStore emailVerificationStore;
	@Autowired UserRepository userRepository;

	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
				.addFilters(springSecurityFilterChain)
				.build();
	}

	@Test
	void anonymousUserCanReadCurrentAgreement() throws Exception {
		mockMvc.perform(get("/api/v1/copyright-agreements/current"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(EmailVerificationTestSupport.CURRENT_COPYRIGHT_AGREEMENT_ID))
				.andExpect(jsonPath("$.version").value("1.0"))
				.andExpect(jsonPath("$.title").value("잇담 저작권 이용 동의서"))
				.andExpect(jsonPath("$.content").isNotEmpty())
				.andExpect(jsonPath("$.validMonths").value(12));
	}

	@Test
	void registrationStoresAgreementAndConsentPeriod() throws Exception {
		String suffix = String.valueOf(System.nanoTime());
		String email = "copyright-registration-" + suffix + "@example.com";

		mockMvc.perform(post("/api/v1/users").with(csrf()).contentType("application/json")
						.content(EmailVerificationTestSupport.verifiedSignupBody(emailVerificationStore, email,
								"password123", "copyright-registration-" + suffix)))
				.andExpect(status().isCreated());

		var user = userRepository.findByEmail(email).orElseThrow();
		assertThat(user.getCopyrightAgreement().getId())
				.isEqualTo(EmailVerificationTestSupport.CURRENT_COPYRIGHT_AGREEMENT_ID);
		assertThat(user.getCopyrightAgreedAt()).isNotNull();
		assertThat(user.getCopyrightAgreementExpiresAt())
				.isEqualTo(user.getCopyrightAgreedAt().atZone(SERVICE_ZONE).plusMonths(12).toInstant());
	}

	@Test
	void registrationRejectsCopyrightAgreementWithoutConsent() throws Exception {
		String suffix = String.valueOf(System.nanoTime());
		String email = "copyright-rejected-" + suffix + "@example.com";
		String verificationToken = EmailVerificationTestSupport.markVerified(emailVerificationStore, email);
		String body = "{\"email\":\"" + email + "\",\"password\":\"password123\","
				+ "\"nickname\":\"copyright-rejected-" + suffix + "\",\"verificationToken\":\""
				+ verificationToken + "\","
				+ "\"copyrightAgreementId\":1,\"copyrightAgreed\":false}";

		mockMvc.perform(post("/api/v1/users").with(csrf()).contentType("application/json").content(body))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
		assertThat(userRepository.findByEmail(email)).isEmpty();
	}

	@Test
	void registrationRejectsNonCurrentCopyrightAgreement() throws Exception {
		String suffix = String.valueOf(System.nanoTime());
		String email = "copyright-mismatch-" + suffix + "@example.com";
		String verificationToken = EmailVerificationTestSupport.markVerified(emailVerificationStore, email);
		String body = "{\"email\":\"" + email + "\",\"password\":\"password123\","
				+ "\"nickname\":\"copyright-mismatch-" + suffix + "\",\"verificationToken\":\""
				+ verificationToken + "\","
				+ "\"copyrightAgreementId\":9999,\"copyrightAgreed\":true}";

		mockMvc.perform(post("/api/v1/users").with(csrf()).contentType("application/json").content(body))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("COPYRIGHT_AGREEMENT_MISMATCH"));
		assertThat(userRepository.findByEmail(email)).isEmpty();
	}
}
