package com.ssafy.c206.backend.sentence.evaluation;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ssafy.c206.backend.word.domain.Word;
import com.ssafy.c206.backend.word.etl.DictionarySense;
import com.ssafy.c206.backend.word.repository.WordRepository;
import com.ssafy.c206.backend.user.EmailVerificationTestSupport;
import com.ssafy.c206.backend.user.service.EmailVerificationStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

@SpringBootTest
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class SentenceEvaluationControllerTest {

	@Autowired private WebApplicationContext webApplicationContext;
	@Autowired private FilterChainProxy springSecurityFilterChain;
	@Autowired private WordRepository wordRepository;
	@Autowired private EmailVerificationStore emailVerificationStore;
	@Autowired private ObjectMapper objectMapper;
	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).addFilters(springSecurityFilterChain).build();
		for (int index = 0; index < 5; index++) {
			wordRepository.save(Word.from(DictionarySense.builder().word("mvc-eval-" + index)
					.partOfSpeech("명사").definition("MVC 평가 단어 " + index).build()));
		}
	}

	@Test
	void evaluatesSynchronouslyAndRetainsGetResult() throws Exception {
		mockMvc.perform(post("/api/v1/users").with(csrf()).contentType("application/json")
				.content(EmailVerificationTestSupport.verifiedSignupBody(
						emailVerificationStore, "evaluation-mvc@example.com", "password123", "evaluation-mvc")))
				.andExpect(status().isCreated());
		MvcResult login = mockMvc.perform(post("/api/v1/auth/sessions").with(csrf()).contentType("application/json")
				.content("{\"email\":\"evaluation-mvc@example.com\",\"password\":\"password123\"}"))
				.andExpect(status().isOk()).andReturn();
		MockHttpSession session = (MockHttpSession) login.getRequest().getSession(false);
		MvcResult create = mockMvc.perform(post("/api/v1/sentences").with(csrf()).session(session).contentType("application/json")
				.content("{\"content\":\"동기 평가 API 문장\"}"))
				.andExpect(status().isCreated()).andReturn();
		long versionId = objectMapper.readTree(create.getResponse().getContentAsString()).path("sentenceVersionId").asLong();

		mockMvc.perform(post("/api/v1/sentence-versions/{versionId}/evaluations", versionId).with(csrf()).session(session))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.evaluationStatus").value("EVALUATED"))
				.andExpect(jsonPath("$.scores.baseScore").value(78.35));
		mockMvc.perform(get("/api/v1/sentence-versions/{versionId}/evaluation", versionId).session(session))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.evaluationStatus").value("EVALUATED"));
	}
}
