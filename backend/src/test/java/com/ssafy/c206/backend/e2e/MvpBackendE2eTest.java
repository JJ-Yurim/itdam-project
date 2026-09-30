package com.ssafy.c206.backend.e2e;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ssafy.c206.backend.book.repository.BookRepository;
import com.ssafy.c206.backend.book.repository.AiJobRepository;
import com.ssafy.c206.backend.boss.repository.BossPhaseRepository;
import com.ssafy.c206.backend.boss.repository.BossRepository;
import com.ssafy.c206.backend.boss.repository.BossRunRepository;
import com.ssafy.c206.backend.common.time.ServiceClock;
import com.ssafy.c206.backend.integration.ai.AiEvaluationClient;
import com.ssafy.c206.backend.integration.ai.AiEvaluationCommand;
import com.ssafy.c206.backend.integration.ai.AiEvaluationResult;
import com.ssafy.c206.backend.integration.ai.FakeAiEvaluationClient;
import com.ssafy.c206.backend.integration.ai.FakeAiEvaluationScenario;
import com.ssafy.c206.backend.integration.book.BookGenerationResult;
import com.ssafy.c206.backend.integration.book.BookGenerationResultHandler;
import com.ssafy.c206.backend.user.repository.UserRepository;
import com.ssafy.c206.backend.user.service.AdminAccountSeeder;
import com.ssafy.c206.backend.word.domain.Word;
import com.ssafy.c206.backend.word.etl.DictionarySense;
import com.ssafy.c206.backend.word.repository.WordRepository;
import com.ssafy.c206.backend.user.EmailVerificationTestSupport;
import com.ssafy.c206.backend.user.service.EmailVerificationStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

/**
 * A service-boundary E2E regression: every actor action traverses the HTTP session APIs,
 * including boss preparation through the admin API. Repositories are used only for the
 * word pool fixture and for assertions.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(MvpBackendE2eTest.ScenarioAiConfiguration.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class MvpBackendE2eTest {

	private static final AtomicInteger SEQUENCE = new AtomicInteger();
	// 공유 H2 규약: 테스트 데이터 식별자는 실행마다 고유값을 쓴다.
	private static final String ADMIN_SUFFIX = String.valueOf(System.nanoTime());
	private static final String ADMIN_EMAIL = "e2e-admin-" + ADMIN_SUFFIX + "@e2e.test";
	private static final String ADMIN_PASSWORD = "admin-password-123";
	private static final String ADMIN_NICKNAME = "e2e-admin-" + ADMIN_SUFFIX;

	@Autowired private WebApplicationContext webApplicationContext;
	@Autowired private FilterChainProxy springSecurityFilterChain;
	@Autowired private ObjectMapper objectMapper;
	@Autowired private WordRepository wordRepository;
	@Autowired private EmailVerificationStore emailVerificationStore;
	@Autowired private BossRepository bossRepository;
	@Autowired private BossRunRepository bossRunRepository;
	@Autowired private BossPhaseRepository bossPhaseRepository;
	@Autowired private BookRepository bookRepository;
	@Autowired private AiJobRepository aiJobRepository;
	@Autowired private UserRepository userRepository;
	@Autowired private PasswordEncoder passwordEncoder;
	@Autowired private ServiceClock serviceClock;
	@Autowired private ScenarioAiClient aiClient;
	@Autowired private BookGenerationResultHandler bookGenerationResultHandler;

	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
				.addFilters(springSecurityFilterChain)
				.build();
		aiClient.configure(FakeAiEvaluationScenario.SUCCESS, Set.of());
	}

	@Test
	void adminPreparesBossAndUserJourneyReachesPublishedBook() throws Exception {
		seedWords();
		MockHttpSession adminSession = loginAdmin();
		long runId = createActiveRun(adminSession, 1L);

		MockHttpSession session = registerAndLogin("happy");

		mockMvc.perform(get("/api/v1/daily-words/today").session(session))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.items.length()").value(5));

		long versionId = createSentence(session, "E2E happy path sentence.");
		mockMvc.perform(get("/api/v1/me/sentences/unevaluated").session(session))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content.length()").value(1))
				.andExpect(jsonPath("$.content[0].sentenceVersionId").value(versionId))
				.andExpect(jsonPath("$.content[0].evaluationStatus").value("DRAFT"));
		mockMvc.perform(post("/api/v1/sentence-versions/{versionId}/evaluations", versionId).with(csrf()).session(session))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.evaluationStatus").value("EVALUATED"));
		mockMvc.perform(get("/api/v1/me/sentences/unevaluated").session(session))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content.length()").value(0));
		mockMvc.perform(get("/api/v1/me/sentences").session(session))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content.length()").value(1))
				.andExpect(jsonPath("$.content[0].sentenceVersionId").value(versionId))
				.andExpect(jsonPath("$.content[0].evaluationStatus").value("EVALUATED"))
				.andExpect(jsonPath("$.content[0].createdAt").isString());

		MvcResult clearing = mockMvc.perform(post("/api/v1/boss-runs/{runId}/submissions", runId).with(csrf()).session(session)
					.contentType("application/json")
					.content("{\"sentenceVersionId\":" + versionId + ",\"submissionConsentAccepted\":true}"))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.bossRunStatus").value("CLEARED"))
				.andReturn();
		long submissionId = readLong(clearing, "submissionId");

		MvcResult pendingBook = mockMvc.perform(get("/api/v1/boss-runs/{runId}/book-generation", runId).session(session))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.bookStatus").value("GENERATING"))
				.andExpect(jsonPath("$.jobStatus").value("PENDING"))
				.andReturn();
		long jobId = readLong(pendingBook, "jobId");
		long bookId = readLong(pendingBook, "bookId");

		// 실제 생성은 MQ 소비자(ISSUE-122)의 몫이므로, 결과 경계를 직접 호출해
		// 제출 원문이 본문으로 실리는 완료 전이를 검증한다.
		var job = aiJobRepository.findById(jobId).orElseThrow();
		bookGenerationResultHandler.handle(BookGenerationResult.success(jobId, job.getCorrelationId(), List.of(
				new BookGenerationResult.Content(1, "AI_GENERATED", "이야기의 서막이 열렸다.", null),
				new BookGenerationResult.Content(2, "USER_ORIGINAL", "E2E happy path sentence.", submissionId)),
				// body는 ai-service가 문단까지 구성해 보내며, 제출 원문을 그대로 담아야 한다.
				"이야기의 서막이 열렸다.\n\nE2E happy path sentence.", "E2E 검증용 요약입니다.",
				List.of(submissionId), List.of(), "test-model", "test-prompt"));

		mockMvc.perform(get("/api/v1/boss-runs/{runId}/book-generation", runId).session(session))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.bookStatus").value("COMPLETED"))
				.andExpect(jsonPath("$.jobStatus").value("SUCCESS"));

		// 도서관 목록은 completedAt 내림차순이므로 방금 완료된 책이 첫 항목이다.
		mockMvc.perform(get("/api/v1/library/books").session(session))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content[0].bookId").value(bookId))
				.andExpect(jsonPath("$.content[0].contentCount").value(2));
		// 본문과 기여 원문·작성자가 도서관 상세에서 열람된다(ISSUE-96).
		mockMvc.perform(get("/api/v1/library/books/{bookId}", bookId).session(session))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.bookId").value(bookId))
				.andExpect(jsonPath("$.bossRunId").value(runId))
				.andExpect(jsonPath("$.contents.length()").value(2))
				.andExpect(jsonPath("$.contents[0].contentType").value("AI_GENERATED"))
				.andExpect(jsonPath("$.contents[0].contributor").doesNotExist())
				.andExpect(jsonPath("$.contents[1].contentType").value("USER_ORIGINAL"))
				.andExpect(jsonPath("$.contents[1].content").value("E2E happy path sentence."))
				.andExpect(jsonPath("$.contents[1].sourceSubmissionId").value(submissionId))
				.andExpect(jsonPath("$.contents[1].contributor.nickname").isNotEmpty());

		mockMvc.perform(post("/api/v1/boss-runs/{runId}/submissions", runId).with(csrf()).session(session)
					.contentType("application/json")
					.content("{\"sentenceVersionId\":" + versionId + ",\"submissionConsentAccepted\":true}"))
				.andExpect(status().isConflict());
	}

	@Test
	void failureBranchesKeepRejectedAndFailedEvaluationsOutOfBooks() throws Exception {
		seedWords();
		MockHttpSession adminSession = loginAdmin();
		MockHttpSession session = registerAndLogin("failure");

		long safetyRejected = createSentence(session, "Safety rejected E2E sentence.");
		aiClient.configure(FakeAiEvaluationScenario.SAFETY_REJECTED, Set.of());
		mockMvc.perform(post("/api/v1/sentence-versions/{versionId}/evaluations", safetyRejected).with(csrf()).session(session))
				.andExpect(status().isUnprocessableEntity())
				.andExpect(jsonPath("$.code").value("POLICY_REJECTED"));
		mockMvc.perform(get("/api/v1/sentence-versions/{versionId}/evaluation", safetyRejected).session(session))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.evaluationStatus").value("REJECTED"));

		long failed = createSentence(session, "Transient failure E2E sentence.");
		aiClient.configure(FakeAiEvaluationScenario.TRANSIENT_FAILURE, Set.of());
		mockMvc.perform(post("/api/v1/sentence-versions/{versionId}/evaluations", failed).with(csrf()).session(session))
				.andExpect(status().isInternalServerError())
				.andExpect(jsonPath("$.code").value("INTERNAL_ERROR"));
		mockMvc.perform(get("/api/v1/sentence-versions/{versionId}/evaluation", failed).session(session))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.evaluationStatus").value("EVALUATION_FAILED"));

		aiClient.configure(FakeAiEvaluationScenario.SUCCESS, Set.of());
		long evaluated = createSentence(session, "Uncleared run E2E sentence.");
		mockMvc.perform(post("/api/v1/sentence-versions/{versionId}/evaluations", evaluated).with(csrf()).session(session))
				.andExpect(status().isOk());
		long unclearedRunId = createActiveRun(adminSession, 100_000L);
		mockMvc.perform(post("/api/v1/boss-runs/{runId}/submissions", unclearedRunId).with(csrf()).session(session)
					.contentType("application/json")
					.content("{\"sentenceVersionId\":" + evaluated + ",\"submissionConsentAccepted\":true}"))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.bossRunStatus").value("ACTIVE"));
		mockMvc.perform(get("/api/v1/boss-runs/{runId}/book-generation", unclearedRunId).session(session))
				.andExpect(status().isNotFound());
		assertThat(bookRepository.findByBossRunId(unclearedRunId)).isEmpty();
	}

	@Test
	void bossPreparationApisRejectNonAdminSessions() throws Exception {
		MockHttpSession session = registerAndLogin("outsider");

		mockMvc.perform(post("/api/admin/bosses").with(csrf()).session(session)
					.contentType("application/json")
					.content("{\"name\":\"Forbidden boss\",\"description\":\"denied\","
							+ "\"moods\":[{\"code\":\"CALM\",\"role\":\"THEME\",\"weight\":1.0}]}"))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("FORBIDDEN"));
		mockMvc.perform(post("/api/admin/boss-runs").with(csrf()).session(session)
					.contentType("application/json")
					.content("{\"bossId\":1,\"endsAt\":\"" + serviceClock.now().plusSeconds(3_600)
							+ "\",\"activate\":true,\"phases\":[{\"maxHp\":1}]}"))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("FORBIDDEN"));
	}

	@Test
	void invalidSignupIsRejectedWhenValidationIsDeclaredOnTheApiContract() throws Exception {
		mockMvc.perform(post("/api/v1/users").with(csrf()).contentType("application/json")
					.content("{\"email\":\"not-an-email\",\"password\":\"short\",\"nickname\":\"\"}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
	}

	private MockHttpSession loginAdmin() throws Exception {
		// 시더는 자체 트랜잭션(repository)만 사용하고 기존 계정을 덮지 않으므로 반복 호출이 안전하다.
		new AdminAccountSeeder(userRepository, passwordEncoder, ADMIN_EMAIL, ADMIN_PASSWORD, ADMIN_NICKNAME).run(null);
		MvcResult login = mockMvc.perform(post("/api/v1/auth/sessions").with(csrf()).contentType("application/json")
					.content("{\"email\":\"" + ADMIN_EMAIL + "\",\"password\":\"" + ADMIN_PASSWORD + "\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.role").value("ADMIN"))
				.andReturn();
		return (MockHttpSession) login.getRequest().getSession(false);
	}

	/** 관리자 API만으로 보스 원형과 활성 회차를 준비한다. maxHp로 클리어 여부를 결정한다. */
	private long createActiveRun(MockHttpSession adminSession, long maxHp) throws Exception {
		int suffix = SEQUENCE.incrementAndGet();
		long imageAssetId = uploadBossImage(adminSession);
		long bookCoverImageAssetId = uploadBookCoverImage(adminSession);
		MvcResult boss = mockMvc.perform(post("/api/admin/bosses").with(csrf()).session(adminSession)
					.contentType("application/json")
					.content("{\"name\":\"E2E Boss " + suffix + "\",\"description\":\"E2E fixture boss\","
							+ "\"bookCoverImageAssetId\":" + bookCoverImageAssetId + ","
							+ "\"moods\":[{\"code\":\"CALM\",\"role\":\"THEME\",\"weight\":1.0},"
							+ "{\"code\":\"TENSION\",\"role\":\"WEAKNESS\",\"weight\":1.5}],"
							+ "\"phaseTemplates\":[{\"maxHp\":" + maxHp
							+ ",\"damageMultiplier\":1.0,\"imageAssetId\":" + imageAssetId + "}]}"))
				.andExpect(status().isCreated())
				.andReturn();
		long bossId = readLong(boss, "id");

		Instant endsAt = serviceClock.now().plusSeconds(3_600);
		MvcResult run = mockMvc.perform(post("/api/admin/boss-runs").with(csrf()).session(adminSession)
					.contentType("application/json")
					.content("{\"bossId\":" + bossId + ",\"endsAt\":\"" + endsAt + "\",\"activate\":true,"
							+ "\"phases\":[{\"maxHp\":" + maxHp + ",\"damageMultiplier\":1.0,"
							+ "\"imageAssetId\":" + imageAssetId + "}]}"))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.status").value("ACTIVE"))
				.andReturn();
		return readLong(run, "id");
	}

	private long uploadBossImage(MockHttpSession adminSession) throws Exception {
		return uploadImage(adminSession, "/api/admin/boss-images", "e2e-phase.png");
	}

	private long uploadBookCoverImage(MockHttpSession adminSession) throws Exception {
		return uploadImage(adminSession, "/api/admin/book-cover-images", "e2e-cover.png");
	}

	private long uploadImage(MockHttpSession adminSession, String endpoint, String filename) throws Exception {
		byte[] png = Base64.getDecoder().decode(
				"iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=");
		MockMultipartFile image = new MockMultipartFile("image", filename, "image/png", png);
		MvcResult upload = mockMvc.perform(multipart(endpoint).file(image)
					.with(csrf()).session(adminSession))
				.andExpect(status().isCreated())
				.andReturn();
		return readLong(upload, "imageAssetId");
	}

	private MockHttpSession registerAndLogin(String prefix) throws Exception {
		int suffix = SEQUENCE.incrementAndGet();
		String email = prefix + suffix + "@e2e.test";
		mockMvc.perform(post("/api/v1/users").with(csrf()).contentType("application/json")
					.content(EmailVerificationTestSupport.verifiedSignupBody(
							emailVerificationStore, email, "password123", prefix + suffix)))
				.andExpect(status().isCreated());
		MvcResult login = mockMvc.perform(post("/api/v1/auth/sessions").with(csrf()).contentType("application/json")
					.content("{\"email\":\"" + email + "\",\"password\":\"password123\"}"))
				.andExpect(status().isOk())
				.andReturn();
		return (MockHttpSession) login.getRequest().getSession(false);
	}

	private long createSentence(MockHttpSession session, String content) throws Exception {
		MvcResult result = mockMvc.perform(post("/api/v1/sentences").with(csrf()).session(session)
					.contentType("application/json").content("{\"content\":\"" + content + "\"}"))
				.andExpect(status().isCreated())
				.andReturn();
		return readLong(result, "id");
	}

	private long readLong(MvcResult result, String field) throws Exception {
		JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
		return json.path(field).asLong();
	}

	private void seedWords() {
		int suffix = SEQUENCE.incrementAndGet();
		for (int index = 0; index < 5; index++) {
			wordRepository.save(Word.from(DictionarySense.builder().word("e2e-" + suffix + "-" + index)
					.partOfSpeech("noun").definition("E2E dictionary definition " + index).build()));
		}
	}

	@TestConfiguration
	static class ScenarioAiConfiguration {
		@Bean
		@Primary
		ScenarioAiClient scenarioAiClient() {
			return new ScenarioAiClient();
		}
	}

	static class ScenarioAiClient implements AiEvaluationClient {
		private volatile FakeAiEvaluationScenario scenario = FakeAiEvaluationScenario.SUCCESS;
		private volatile Set<Long> usedDailyWordIds = Set.of();

		void configure(FakeAiEvaluationScenario scenario, Set<Long> usedDailyWordIds) {
			this.scenario = scenario;
			this.usedDailyWordIds = Set.copyOf(usedDailyWordIds);
		}

		@Override
		public AiEvaluationResult evaluate(AiEvaluationCommand command) {
			return new FakeAiEvaluationClient(scenario, usedDailyWordIds).evaluate(command);
		}
	}
}
