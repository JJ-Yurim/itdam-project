package com.ssafy.c206.backend.user;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

// IP 카운터는 컨텍스트 전역 상태라 다른 시나리오와 간섭하지 않도록 전용 컨텍스트에서 검증한다.
@SpringBootTest(properties = "email.verification.ip-hourly-limit=3")
@ActiveProfiles("test")
class EmailVerificationIpRateLimitTest {

	@Autowired
	WebApplicationContext webApplicationContext;

	@Autowired
	FilterChainProxy springSecurityFilterChain;

	MockMvc mockMvc;

	@BeforeEach
	void setUpMockMvc() {
		mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
				.addFilters(springSecurityFilterChain)
				.build();
	}

	@Test
	void sendsBeyondIpLimitAreRateLimitedEvenForDifferentEmails() throws Exception {
		for (int i = 1; i <= 3; i++) {
			mockMvc.perform(post("/api/v1/email-verifications").with(csrf())
							.contentType("application/json")
							.content("{\"email\":\"ip-limit-" + i + "@example.com\"}"))
					.andExpect(status().isAccepted());
		}
		mockMvc.perform(post("/api/v1/email-verifications").with(csrf())
						.contentType("application/json")
						.content("{\"email\":\"ip-limit-4@example.com\"}"))
				.andExpect(status().isTooManyRequests())
				.andExpect(jsonPath("$.code").value("RATE_LIMITED"));
	}
}
