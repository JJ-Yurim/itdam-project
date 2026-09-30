package com.ssafy.c206.backend.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ssafy.c206.backend.user.service.EmailVerificationStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

// IP 상한은 별도 컨텍스트(EmailVerificationIpRateLimitTest)에서 검증하므로 여기서는 걸리지 않게 올려 둔다.
@SpringBootTest(properties = "email.verification.ip-hourly-limit=1000")
@ActiveProfiles("test")
class EmailVerificationControllerTest {

	@Autowired
	WebApplicationContext webApplicationContext;

	@Autowired
	FilterChainProxy springSecurityFilterChain;

	@Autowired
	EmailVerificationStore store;

	@Autowired
	ObjectMapper objectMapper;

	MockMvc mockMvc;

	@BeforeEach
	void setUpMockMvc() {
		mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
				.addFilters(springSecurityFilterChain)
				.build();
	}

	private String confirmAndGetToken(String email, String code) throws Exception {
		MvcResult result = mockMvc.perform(post("/api/v1/email-verifications/confirm").with(csrf())
						.contentType("application/json")
						.content("{\"email\":\"" + email + "\",\"code\":\"" + code + "\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.verificationToken").isString())
				.andExpect(jsonPath("$.validForSeconds").value(600))
				.andReturn();
		return objectMapper.readTree(result.getResponse().getContentAsString())
				.path("verificationToken").asText();
	}

	@Test
	void sendAndConfirmIssuesTokenAndConsumesCode() throws Exception {
		String email = "verify-flow@example.com";
		mockMvc.perform(post("/api/v1/email-verifications").with(csrf())
						.contentType("application/json").content("{\"email\":\"" + email + "\"}"))
				.andExpect(status().isAccepted())
				.andExpect(jsonPath("$.expiresInSeconds").value(300))
				.andExpect(jsonPath("$.resendCooldownSeconds").value(60));

		String code = store.findCode(email).orElseThrow();
		String token = confirmAndGetToken(email, code);

		assertThat(store.findVerifiedToken(email)).contains(token);
		assertThat(store.findCode(email)).isEmpty();
	}

	@Test
	void fullVerificationFlowAllowsSignupEvenWithGmailAlias() throws Exception {
		// 발송·확인·가입 전 구간에서 Gmail 별칭(+tag)·대문자 표기가 같은 이메일로 정규화되는지 검증한다.
		mockMvc.perform(post("/api/v1/email-verifications").with(csrf())
						.contentType("application/json").content("{\"email\":\"Flow+alias@Gmail.com\"}"))
				.andExpect(status().isAccepted());

		String code = store.findCode("flow@gmail.com").orElseThrow();
		String token = confirmAndGetToken("flow+alias@gmail.com", code);

		mockMvc.perform(post("/api/v1/users").with(csrf()).contentType("application/json")
						.content("{\"email\":\"flow@gmail.com\",\"password\":\"password123\","
								+ "\"nickname\":\"flowuser\",\"verificationToken\":\"" + token + "\","
								+ "\"copyrightAgreementId\":1,\"copyrightAgreed\":true}"))
				.andExpect(status().isCreated());
		assertThat(store.findVerifiedToken("flow@gmail.com")).isEmpty();
	}

	@Test
	void uppercaseEmailIsNormalizedToSameVerification() throws Exception {
		String email = "verify-case@example.com";
		mockMvc.perform(post("/api/v1/email-verifications").with(csrf())
						.contentType("application/json").content("{\"email\":\"Verify-Case@Example.com\"}"))
				.andExpect(status().isAccepted());

		String code = store.findCode(email).orElseThrow();
		confirmAndGetToken(email, code);
		assertThat(store.findVerifiedToken(email)).isPresent();
	}

	@Test
	void fifthWrongAttemptInvalidatesCode() throws Exception {
		String email = "verify-attempts@example.com";
		mockMvc.perform(post("/api/v1/email-verifications").with(csrf())
						.contentType("application/json").content("{\"email\":\"" + email + "\"}"))
				.andExpect(status().isAccepted());

		String code = store.findCode(email).orElseThrow();
		String wrongCode = code.equals("111111") ? "222222" : "111111";
		String wrongBody = "{\"email\":\"" + email + "\",\"code\":\"" + wrongCode + "\"}";

		for (int attempt = 1; attempt <= 4; attempt++) {
			mockMvc.perform(post("/api/v1/email-verifications/confirm").with(csrf())
							.contentType("application/json").content(wrongBody))
					.andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
		}
		mockMvc.perform(post("/api/v1/email-verifications/confirm").with(csrf())
						.contentType("application/json").content(wrongBody))
				.andExpect(status().isTooManyRequests());

		// 무효화 이후에는 올바른 코드도 더 이상 통하지 않는다.
		mockMvc.perform(post("/api/v1/email-verifications/confirm").with(csrf())
						.contentType("application/json")
						.content("{\"email\":\"" + email + "\",\"code\":\"" + code + "\"}"))
				.andExpect(status().isNotFound());
		assertThat(store.findVerifiedToken(email)).isEmpty();
	}

	@Test
	void resendWithinCooldownIsRateLimited() throws Exception {
		String body = "{\"email\":\"verify-cooldown@example.com\"}";
		mockMvc.perform(post("/api/v1/email-verifications").with(csrf())
						.contentType("application/json").content(body))
				.andExpect(status().isAccepted());
		mockMvc.perform(post("/api/v1/email-verifications").with(csrf())
						.contentType("application/json").content(body))
				.andExpect(status().isTooManyRequests())
				.andExpect(jsonPath("$.code").value("RATE_LIMITED"));
	}

	@Test
	void confirmWithoutIssuedCodeReturnsNotFound() throws Exception {
		mockMvc.perform(post("/api/v1/email-verifications/confirm").with(csrf())
						.contentType("application/json")
						.content("{\"email\":\"verify-none@example.com\",\"code\":\"123456\"}"))
				.andExpect(status().isNotFound());
	}

	@Test
	void sendRequiresCsrfToken() throws Exception {
		mockMvc.perform(post("/api/v1/email-verifications")
						.contentType("application/json").content("{\"email\":\"verify-csrf@example.com\"}"))
				.andExpect(status().isForbidden());
	}

	@Test
	void malformedEmailAndCodeAreRejected() throws Exception {
		mockMvc.perform(post("/api/v1/email-verifications").with(csrf())
						.contentType("application/json").content("{\"email\":\"not-an-email\"}"))
				.andExpect(status().isBadRequest());
		mockMvc.perform(post("/api/v1/email-verifications/confirm").with(csrf())
						.contentType("application/json")
						.content("{\"email\":\"verify-format@example.com\",\"code\":\"12ab56\"}"))
				.andExpect(status().isBadRequest());
	}
}
