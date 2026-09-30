package com.ssafy.c206.backend.sentence;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
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
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

@SpringBootTest
@ActiveProfiles("test")
class SentenceControllerTest {

	@Autowired
	private WebApplicationContext webApplicationContext;

	@Autowired
	private FilterChainProxy springSecurityFilterChain;

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private EmailVerificationStore emailVerificationStore;

	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
				.addFilters(springSecurityFilterChain).build();
	}

	@Test
	void authenticatedUserCanCreateUpdateReadAndDeleteSentence() throws Exception {
		String suffix = String.valueOf(System.nanoTime());
		String signup = EmailVerificationTestSupport.verifiedSignupBody(emailVerificationStore,
				"sentence-" + suffix + "@example.com", "password123", "sentence-" + suffix);
		mockMvc.perform(post("/api/v1/users").with(csrf()).contentType("application/json").content(signup))
				.andExpect(status().isCreated());
		MvcResult login = mockMvc.perform(post("/api/v1/auth/sessions").with(csrf()).contentType("application/json").content(
					"{\"email\":\"sentence-" + suffix + "@example.com\",\"password\":\"password123\"}"))
				.andExpect(status().isOk()).andReturn();
		MockHttpSession session = (MockHttpSession) login.getRequest().getSession(false);

		MvcResult create = mockMvc.perform(post("/api/v1/sentences").with(csrf()).session(session).contentType("application/json")
					.content("{\"content\":\"  첫 문장  \"}"))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.versionNo").value(1))
				.andExpect(jsonPath("$.evaluationStatus").value("DRAFT"))
				.andExpect(jsonPath("$.content").value("첫 문장"))
				.andReturn();
		JsonNode created = objectMapper.readTree(create.getResponse().getContentAsString());
		long sentenceId = created.get("sentenceId").asLong();
		long versionId = created.get("id").asLong();

		mockMvc.perform(patch("/api/v1/sentence-versions/{versionId}", versionId).with(csrf()).session(session)
					.contentType("application/json").content("{\"content\":\"수정 문장\"}"))
				.andExpect(status().isOk()).andExpect(jsonPath("$.content").value("수정 문장"));
		mockMvc.perform(get("/api/v1/sentences/{sentenceId}", sentenceId).session(session))
				.andExpect(status().isOk()).andExpect(jsonPath("$.versions[0].content").value("수정 문장"));
		mockMvc.perform(delete("/api/v1/sentences/{sentenceId}", sentenceId).with(csrf()).session(session))
				.andExpect(status().isNoContent());
	}
}
