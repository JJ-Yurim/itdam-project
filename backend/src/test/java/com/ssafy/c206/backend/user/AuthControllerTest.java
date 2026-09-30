package com.ssafy.c206.backend.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ssafy.c206.backend.user.service.EmailVerificationStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.security.web.FilterChainProxy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

@SpringBootTest
@ActiveProfiles("test")
class AuthControllerTest {

	@Autowired
	WebApplicationContext webApplicationContext;

	@Autowired
	FilterChainProxy springSecurityFilterChain;

	@Autowired
	EmailVerificationStore emailVerificationStore;

	MockMvc mockMvc;

	@BeforeEach
	void setUpMockMvc() {
		mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
				.addFilters(springSecurityFilterChain)
				.build();
	}

	private String markVerified(String email) {
		return EmailVerificationTestSupport.markVerified(emailVerificationStore, email);
	}

	private String signupBody(String email, String nickname) {
		return EmailVerificationTestSupport.verifiedSignupBody(emailVerificationStore, email, "password123", nickname);
	}

	@Test
	void registrationLoginPrincipalMeAndLogoutUseHttpSession() throws Exception {
		mockMvc.perform(post("/api/v1/users").with(csrf()).contentType("application/json")
						.content(signupBody("wave2@example.com", "wave2")))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.email").value("wave2@example.com"))
				.andExpect(jsonPath("$.status").value("ACTIVE"));

		String login = "{\"email\":\"wave2@example.com\",\"password\":\"password123\"}";
		MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/sessions").with(csrf())
				.contentType("application/json").content(login))
				.andExpect(status().isOk())
				.andReturn();
		MockHttpSession session = (MockHttpSession) loginResult.getRequest().getSession(false);

		mockMvc.perform(get("/api/v1/users/me").session(session))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.email").value("wave2@example.com"));

		mockMvc.perform(post("/api/v1/auth/sessions/current").with(csrf()).session(session))
				.andExpect(status().isNoContent());
		mockMvc.perform(get("/api/v1/users/me").session(session))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void duplicateSignupAndInvalidLoginAreRejected() throws Exception {
		mockMvc.perform(post("/api/v1/users").with(csrf()).contentType("application/json")
						.content(signupBody("duplicate@example.com", "duplicate")))
				.andExpect(status().isCreated());
		mockMvc.perform(post("/api/v1/users").with(csrf()).contentType("application/json")
						.content(signupBody("duplicate@example.com", "duplicate")))
				.andExpect(status().isConflict());
		mockMvc.perform(post("/api/v1/auth/sessions").with(csrf()).contentType("application/json")
					.content("{\"email\":\"duplicate@example.com\",\"password\":\"wrong-password\"}"))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void signupWithoutEmailVerificationIsRejected() throws Exception {
		// 표식 없이 임의 토큰만 보낸 요청은 거절된다.
		String signup = "{\"email\":\"unverified@example.com\",\"password\":\"password123\","
				+ "\"nickname\":\"unverified\",\"verificationToken\":\"forged-token\","
				+ "\"copyrightAgreementId\":1,\"copyrightAgreed\":true}";
		mockMvc.perform(post("/api/v1/users").with(csrf()).contentType("application/json").content(signup))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("EMAIL_NOT_VERIFIED"));
	}

	@Test
	void signupWithMismatchedVerificationTokenIsRejected() throws Exception {
		// 표식은 있어도(피해자가 인증 완료) 토큰이 다른 요청(제3자 선점 시도)은 거절된다.
		markVerified("victim@example.com");
		String signup = "{\"email\":\"victim@example.com\",\"password\":\"password123\","
				+ "\"nickname\":\"attacker\",\"verificationToken\":\"stolen-guess\","
				+ "\"copyrightAgreementId\":1,\"copyrightAgreed\":true}";
		mockMvc.perform(post("/api/v1/users").with(csrf()).contentType("application/json").content(signup))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("EMAIL_NOT_VERIFIED"));
		// 표식은 남아 있어 피해자 본인은 자기 토큰으로 가입할 수 있다.
		assertThat(emailVerificationStore.findVerifiedToken("victim@example.com")).isPresent();
	}

	@Test
	void verifiedFlagIsConsumedBySuccessfulSignup() throws Exception {
		mockMvc.perform(post("/api/v1/users").with(csrf()).contentType("application/json")
						.content(signupBody("consume@example.com", "consume")))
				.andExpect(status().isCreated());
		assertThat(emailVerificationStore.findVerifiedToken("consume@example.com")).isEmpty();
	}

	@Test
	void failedSignupKeepsVerifiedFlagForRetry() throws Exception {
		mockMvc.perform(post("/api/v1/users").with(csrf()).contentType("application/json")
						.content(signupBody("taken-nick@example.com", "retrynick")))
				.andExpect(status().isCreated());

		// 닉네임 충돌로 가입이 실패하면 표식은 남아 있어, 닉네임만 바꿔 재인증 없이 재시도할 수 있다.
		mockMvc.perform(post("/api/v1/users").with(csrf()).contentType("application/json")
						.content(signupBody("retry2@example.com", "retrynick")))
				.andExpect(status().isConflict());
		assertThat(emailVerificationStore.findVerifiedToken("retry2@example.com")).isPresent();
	}

	@Test
	void gmailPlusTagIsTreatedAsDuplicateEmail() throws Exception {
		mockMvc.perform(post("/api/v1/users").with(csrf()).contentType("application/json")
						.content(signupBody("canon@gmail.com", "canon")))
				.andExpect(status().isCreated());

		// +tag 별칭과 대문자 표기는 같은 이메일로 정규화되어 중복으로 판정된다.
		String aliasToken = markVerified("Canon+promo@Gmail.com");
		String aliased = "{\"email\":\"Canon+promo@Gmail.com\",\"password\":\"password123\","
				+ "\"nickname\":\"canon2\",\"verificationToken\":\"" + aliasToken + "\","
				+ "\"copyrightAgreementId\":1,\"copyrightAgreed\":true}";
		mockMvc.perform(post("/api/v1/users").with(csrf()).contentType("application/json").content(aliased))
				.andExpect(status().isConflict());
	}

	@Test
	void userIdHeaderCannotAuthenticateRequest() throws Exception {
		mockMvc.perform(get("/api/v1/users/me").header("X-User-Id", "1"))
				.andExpect(status().isUnauthorized());
	}
}
