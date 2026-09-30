package com.ssafy.c206.backend.common;

import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ssafy.c206.backend.user.EmailVerificationTestSupport;
import com.ssafy.c206.backend.user.service.EmailVerificationStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/**
 * API 계약 인터페이스에 선언된 @Valid가 구현 컨트롤러에서 실제로 동작하는지 검증한다.
 * 리팩터링 안전망: 검증이 무너지면 400 대신 다른 상태 코드가 반환되어 즉시 실패한다.
 */
@SpringBootTest
@ActiveProfiles("test")
class RequestValidationTest {

	@Autowired
	private WebApplicationContext webApplicationContext;

	@Autowired
	private FilterChainProxy springSecurityFilterChain;

	@Autowired
	private EmailVerificationStore emailVerificationStore;

	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
				.addFilters(springSecurityFilterChain).build();
	}

	@Test
	void blankSentenceContentIsRejectedWithValidationError() throws Exception {
		MockHttpSession session = authenticatedSession("valid-blank");

		mockMvc.perform(post("/api/v1/sentences").with(csrf()).session(session)
					.contentType("application/json").content("{\"content\":\"   \"}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
				.andExpect(jsonPath("$.fieldErrors[0].field").value("content"))
				.andExpect(jsonPath("$.occurredAt", matchesPattern("\\d{4}-\\d{2}-\\d{2}T[0-9:.]+Z")));
	}

	@Test
	void oversizedSentenceContentIsRejectedWithValidationError() throws Exception {
		MockHttpSession session = authenticatedSession("valid-size");
		String oversized = "가".repeat(501);

		mockMvc.perform(post("/api/v1/sentences").with(csrf()).session(session)
					.contentType("application/json").content("{\"content\":\"" + oversized + "\"}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
				.andExpect(jsonPath("$.fieldErrors[0].field").value("content"));
	}

	private MockHttpSession authenticatedSession(String prefix) throws Exception {
		String suffix = String.valueOf(System.nanoTime());
		String email = prefix + "-" + suffix + "@example.com";
		String signup = EmailVerificationTestSupport.verifiedSignupBody(
				emailVerificationStore, email, "password123", prefix + "-" + suffix);
		mockMvc.perform(post("/api/v1/users").with(csrf()).contentType("application/json").content(signup))
				.andExpect(status().isCreated());
		MvcResult login = mockMvc.perform(post("/api/v1/auth/sessions").with(csrf())
					.contentType("application/json")
					.content("{\"email\":\"" + email + "\",\"password\":\"password123\"}"))
				.andExpect(status().isOk()).andReturn();
		return (MockHttpSession) login.getRequest().getSession(false);
	}
}
