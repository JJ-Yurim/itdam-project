package com.ssafy.c206.backend.sentence.archive;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
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

@SpringBootTest
@ActiveProfiles("test")
class ArchiveControllerTest {

	@Autowired private WebApplicationContext webApplicationContext;
	@Autowired private FilterChainProxy springSecurityFilterChain;
	@Autowired private ObjectMapper objectMapper;
	@Autowired private EmailVerificationStore emailVerificationStore;

	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
				.addFilters(springSecurityFilterChain)
				.build();
	}

	@Test
	void authenticatedUserCanReadUnevaluatedArchiveWithCommonFields() throws Exception {
		MockHttpSession session = registerAndLogin("archive-http");
		MvcResult created = mockMvc.perform(post("/api/v1/sentences").with(csrf()).session(session)
						.contentType("application/json").content("{\"content\":\"탭 공통 문장\"}"))
				.andExpect(status().isCreated())
				.andReturn();
		long versionId = objectMapper.readTree(created.getResponse().getContentAsString()).path("id").asLong();

		mockMvc.perform(get("/api/v1/me/sentences/unevaluated")
						.param("page", "0").param("size", "20").session(session))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content.length()").value(1))
				.andExpect(jsonPath("$.content[0].sentenceVersionId").value(versionId))
				.andExpect(jsonPath("$.content[0].sentenceId").isNumber())
				.andExpect(jsonPath("$.content[0].versionNo").value(1))
				.andExpect(jsonPath("$.content[0].content").value("탭 공통 문장"))
				.andExpect(jsonPath("$.content[0].evaluationStatus").value("DRAFT"))
				.andExpect(jsonPath("$.content[0].createdAt").isString())
				.andExpect(jsonPath("$.content[0].baseScore").doesNotExist());
	}

	@Test
	void unevaluatedArchiveRequiresAuthenticationAndValidPagination() throws Exception {
		mockMvc.perform(get("/api/v1/me/sentences/unevaluated"))
				.andExpect(status().isUnauthorized());

		MockHttpSession session = registerAndLogin("archive-validation");
		mockMvc.perform(get("/api/v1/me/sentences/unevaluated").param("size", "101").session(session))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
		mockMvc.perform(get("/api/v1/me/sentences/unevaluated").param("page", "-1").session(session))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
	}

	private MockHttpSession registerAndLogin(String prefix) throws Exception {
		String suffix = String.valueOf(System.nanoTime());
		String email = prefix + "-" + suffix + "@example.com";
		String signup = EmailVerificationTestSupport.verifiedSignupBody(
				emailVerificationStore, email, "password123", prefix + "-" + suffix);
		mockMvc.perform(post("/api/v1/users").with(csrf()).contentType("application/json").content(signup))
				.andExpect(status().isCreated());
		MvcResult login = mockMvc.perform(post("/api/v1/auth/sessions").with(csrf())
						.contentType("application/json")
						.content("{\"email\":\"" + email + "\",\"password\":\"password123\"}"))
				.andExpect(status().isOk())
				.andReturn();
		return (MockHttpSession) login.getRequest().getSession(false);
	}
}
