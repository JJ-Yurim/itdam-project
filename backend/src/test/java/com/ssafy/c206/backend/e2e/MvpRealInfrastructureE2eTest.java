package com.ssafy.c206.backend.e2e;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicInteger;

import javax.sql.DataSource;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ssafy.c206.backend.book.domain.AiJobStatus;
import com.ssafy.c206.backend.book.domain.BookStatus;
import com.ssafy.c206.backend.book.repository.AiJobRepository;
import com.ssafy.c206.backend.book.repository.BookContentRepository;
import com.ssafy.c206.backend.book.repository.BookRepository;
import com.ssafy.c206.backend.common.time.ServiceClock;
import com.ssafy.c206.backend.integration.book.BookGenerationMqContract;
import com.ssafy.c206.backend.user.EmailVerificationTestSupport;
import com.ssafy.c206.backend.user.repository.UserRepository;
import com.ssafy.c206.backend.user.service.AdminAccountSeeder;
import com.ssafy.c206.backend.user.service.EmailVerificationStore;
import com.ssafy.c206.backend.word.domain.Word;
import com.ssafy.c206.backend.word.etl.DictionarySense;
import com.ssafy.c206.backend.word.repository.WordRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * 프론트를 제외한 MVP 실제 인프라 종단 회귀 테스트.
 *
 * <p>모든 사용자·관리자 행위는 MockMvc HTTP 경계를 통과한다. 평가와 책 생성만
 * 외부 Gemini 대신 같은 FastAPI 애플리케이션의 결정적 계약 provider를 사용한다.
 * 저장소 접근은 최종 영속 상태와 DLQ 공백을 확인할 때만 사용한다.
 */
@Tag("infra")
@SpringBootTest
@ActiveProfiles("postgres-test")
@Testcontainers
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class MvpRealInfrastructureE2eTest {

	private static final int AI_SERVICE_PORT = 8001;
	private static final String RABBIT_USER = "mvp_e2e";
	private static final String RABBIT_PASSWORD = "mvp_e2e_password";
	private static final String READONLY_PASSWORD = "e2e-readonly-password";
	private static final Network NETWORK = Network.newNetwork();
	private static final AtomicInteger SEQUENCE = new AtomicInteger();
	private static final String ADMIN_EMAIL = "real-e2e-admin-" + System.nanoTime() + "@e2e.test";
	private static final String ADMIN_PASSWORD = "admin-password-123";

	@Container
	static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
			DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"))
			.withNetwork(NETWORK)
			.withNetworkAliases("postgres");

	@Container
	static final RabbitMQContainer RABBIT = new RabbitMQContainer(
			DockerImageName.parse("rabbitmq:3.13-management-alpine").asCompatibleSubstituteFor("rabbitmq"))
			.withNetwork(NETWORK)
			.withNetworkAliases("rabbitmq")
			.withAdminUser(RABBIT_USER)
			.withAdminPassword(RABBIT_PASSWORD);

	@Container
	static final GenericContainer<?> AI_SERVICE = createAiServiceContainer().dependsOn(RABBIT, POSTGRES);

	@DynamicPropertySource
	static void configureInfrastructure(DynamicPropertyRegistry registry) {
		registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
		registry.add("spring.datasource.username", POSTGRES::getUsername);
		registry.add("spring.datasource.password", POSTGRES::getPassword);
		registry.add("ai.evaluation.client", () -> "http");
		registry.add("ai.evaluation.base-url", () -> "http://" + AI_SERVICE.getHost()
				+ ":" + AI_SERVICE.getMappedPort(AI_SERVICE_PORT));
		registry.add("ai.evaluation.contract-version", () -> "sentence-evaluation-v1");
		registry.add("book.generation.client", () -> "rabbit");
		registry.add("book.generation.dispatch-recovery-delay-ms", () -> "60000");
		registry.add("spring.rabbitmq.host", RABBIT::getHost);
		registry.add("spring.rabbitmq.port", RABBIT::getAmqpPort);
		registry.add("spring.rabbitmq.username", () -> RABBIT_USER);
		registry.add("spring.rabbitmq.password", () -> RABBIT_PASSWORD);
		// V7이 SELECT 전용 계정을 실제로 만들도록 플레이스홀더를 채운다.
		registry.add("spring.flyway.placeholders.ai_readonly_password", () -> READONLY_PASSWORD);
	}

	@Autowired private WebApplicationContext webApplicationContext;
	@Autowired private FilterChainProxy springSecurityFilterChain;
	@Autowired private ObjectMapper objectMapper;
	@Autowired private WordRepository wordRepository;
	@Autowired private EmailVerificationStore emailVerificationStore;
	@Autowired private UserRepository userRepository;
	@Autowired private PasswordEncoder passwordEncoder;
	@Autowired private ServiceClock serviceClock;
	@Autowired private AiJobRepository aiJobRepository;
	@Autowired private BookRepository bookRepository;
	@Autowired private BookContentRepository contentRepository;
	@Autowired private RabbitTemplate rabbitTemplate;
	@Autowired private DataSource dataSource;
	@Autowired private JdbcTemplate jdbcTemplate;

	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
				.addFilters(springSecurityFilterChain)
				.build();
	}

	@Test
	void validSentenceTravelsFromHttpEvaluationThroughBossAndMqToPublicLibrary() throws Exception {
		try (var connection = dataSource.getConnection()) {
			assertThat(connection.getMetaData().getDatabaseProductName()).isEqualToIgnoringCase("PostgreSQL");
		}
		seedWords();
		MockHttpSession adminSession = loginAdmin();
		long runId = createActiveRun(adminSession);
		MockHttpSession memberSession = registerAndLogin();

		mockMvc.perform(get("/api/v1/daily-words/today").session(memberSession))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.items.length()").value(5));

		String sentence = "실제 인프라를 지나 완성된 책으로 이어지는 문장입니다.";
		long versionId = createSentence(memberSession, sentence);
		mockMvc.perform(post("/api/v1/sentence-versions/{versionId}/evaluations", versionId)
					.with(csrf()).session(memberSession))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.evaluationStatus").value("EVALUATED"))
				.andExpect(jsonPath("$.modelName").value("contract-provider"));
		assertThat(jdbcTemplate.queryForObject("""
				select count(*) from sentence_embeddings
				where sentence_version_id = ? and model_name = 'contract-embedding-v1' and dimension = 768
				""", Integer.class, versionId)).isOne();

		MvcResult clearing = mockMvc.perform(post("/api/v1/boss-runs/{runId}/submissions", runId)
					.with(csrf()).session(memberSession)
					.contentType("application/json")
					.content("{\"sentenceVersionId\":" + versionId
							+ ",\"submissionConsentAccepted\":true}"))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.bossRunStatus").value("CLEARED"))
				.andReturn();
		long submissionId = readLong(clearing, "submissionId");

		BookCompletion completion = awaitBookCompletion(memberSession, runId);
		assertThat(completion.jobStatus()).isEqualTo(AiJobStatus.SUCCESS.name());
		assertThat(completion.bookStatus()).isEqualTo(BookStatus.COMPLETED.name());

		mockMvc.perform(get("/api/v1/library/books").session(memberSession))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content[0].bookId").value(completion.bookId()))
				.andExpect(jsonPath("$.content[0].contentCount").value(2))
				// 목록 미리보기는 요약을 쓴다. 본문 전문은 상세에서만 내려간다.
				.andExpect(jsonPath("$.content[0].summary").isNotEmpty());
		mockMvc.perform(get("/api/v1/library/books/{bookId}", completion.bookId()).session(memberSession))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.bossRunId").value(runId))
				.andExpect(jsonPath("$.contents.length()").value(2))
				.andExpect(jsonPath("$.contents[0].contentType").value("AI_GENERATED"))
				.andExpect(jsonPath("$.contents[1].contentType").value("USER_ORIGINAL"))
				.andExpect(jsonPath("$.contents[1].content").value(sentence))
				.andExpect(jsonPath("$.contents[1].sourceSubmissionId").value(submissionId))
				.andExpect(jsonPath("$.contents[1].contributor.userId").isNumber())
				// 문단까지 구성된 전문이 ai-service에서 온 그대로 저장·노출된다. Spring이 블록을
				// 조합하면 문단(빈 줄)이 사라지므로 그 구분이 남아 있는지까지 본다.
				.andExpect(jsonPath("$.body").value(containsString(sentence)))
				.andExpect(jsonPath("$.body").value(containsString("\n\n")));

		// 완성된 책은 비회원에게도 공개되지만 내부 사용자·제출 식별자는 숨긴다.
		mockMvc.perform(get("/api/v1/library/books/{bookId}", completion.bookId()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.contents[1].content").value(sentence))
				.andExpect(jsonPath("$.contents[1].contributor.nickname").isNotEmpty())
				.andExpect(jsonPath("$.contents[1].contributor.userId").isEmpty())
				.andExpect(jsonPath("$.contents[1].sourceSubmissionId").isEmpty());

		var job = aiJobRepository.findById(completion.jobId()).orElseThrow();
		var book = bookRepository.findById(completion.bookId()).orElseThrow();
		assertThat(job.isRequestDispatched()).isTrue();
		assertThat(job.getModelName()).isEqualTo("contract-provider");
		assertThat(job.getPromptVersion()).isEqualTo("contract-book-prompt-v1");
		assertThat(book.getStatus()).isEqualTo(BookStatus.COMPLETED);
		assertThat(contentRepository.countByBookId(book.getId())).isEqualTo(2);
		assertThat(rabbitTemplate.receive(BookGenerationMqContract.REQUEST_DLQ, 500)).isNull();
		assertThat(rabbitTemplate.receive(BookGenerationMqContract.RESULT_DLQ, 500)).isNull();
	}

	private MockHttpSession loginAdmin() throws Exception {
		new AdminAccountSeeder(userRepository, passwordEncoder, ADMIN_EMAIL, ADMIN_PASSWORD,
				"real-e2e-admin").run(null);
		MvcResult login = mockMvc.perform(post("/api/v1/auth/sessions").with(csrf())
					.contentType("application/json")
					.content("{\"email\":\"" + ADMIN_EMAIL + "\",\"password\":\""
							+ ADMIN_PASSWORD + "\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.role").value("ADMIN"))
				.andReturn();
		return (MockHttpSession) login.getRequest().getSession(false);
	}

	private MockHttpSession registerAndLogin() throws Exception {
		String suffix = String.valueOf(SEQUENCE.incrementAndGet());
		String email = "real-e2e-user-" + suffix + "@e2e.test";
		mockMvc.perform(post("/api/v1/users").with(csrf()).contentType("application/json")
					.content(EmailVerificationTestSupport.verifiedSignupBody(
							emailVerificationStore, email, "password123", "real-e2e-" + suffix)))
				.andExpect(status().isCreated());
		MvcResult login = mockMvc.perform(post("/api/v1/auth/sessions").with(csrf())
					.contentType("application/json")
					.content("{\"email\":\"" + email + "\",\"password\":\"password123\"}"))
				.andExpect(status().isOk())
				.andReturn();
		return (MockHttpSession) login.getRequest().getSession(false);
	}

	private long createActiveRun(MockHttpSession adminSession) throws Exception {
		int suffix = SEQUENCE.incrementAndGet();
		long imageAssetId = uploadBossImage(adminSession);
		long bookCoverImageAssetId = uploadBookCoverImage(adminSession);
		MvcResult boss = mockMvc.perform(post("/api/admin/bosses").with(csrf()).session(adminSession)
					.contentType("application/json")
					.content("{\"name\":\"Real E2E Boss " + suffix
							+ "\",\"description\":\"PostgreSQL RabbitMQ FastAPI E2E\","
							+ "\"bookCoverImageAssetId\":" + bookCoverImageAssetId + ","
							+ "\"moods\":[{\"code\":\"CALM\",\"role\":\"THEME\",\"weight\":1.0}],"
							+ "\"phaseTemplates\":[{\"maxHp\":1,\"damageMultiplier\":1.0,"
							+ "\"imageAssetId\":" + imageAssetId + "}]}"))
				.andExpect(status().isCreated())
				.andReturn();
		long bossId = readLong(boss, "id");
		Instant endsAt = serviceClock.now().plusSeconds(3_600);
		MvcResult run = mockMvc.perform(post("/api/admin/boss-runs").with(csrf()).session(adminSession)
					.contentType("application/json")
					.content("{\"bossId\":" + bossId + ",\"endsAt\":\"" + endsAt
							+ "\",\"activate\":true,\"phases\":[{\"maxHp\":1,\"damageMultiplier\":1.0,"
							+ "\"imageAssetId\":" + imageAssetId + "}]}"))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.status").value("ACTIVE"))
				.andReturn();
		return readLong(run, "id");
	}

	private long uploadBossImage(MockHttpSession adminSession) throws Exception {
		return uploadImage(adminSession, "/api/admin/boss-images", "real-e2e-phase.png");
	}

	private long uploadBookCoverImage(MockHttpSession adminSession) throws Exception {
		return uploadImage(adminSession, "/api/admin/book-cover-images", "real-e2e-cover.png");
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

	private long createSentence(MockHttpSession session, String content) throws Exception {
		MvcResult created = mockMvc.perform(post("/api/v1/sentences").with(csrf()).session(session)
					.contentType("application/json")
					.content("{\"content\":\"" + content + "\"}"))
				.andExpect(status().isCreated())
				.andReturn();
		return readLong(created, "id");
	}

	private BookCompletion awaitBookCompletion(MockHttpSession session, long runId) throws Exception {
		long deadline = System.nanoTime() + Duration.ofSeconds(30).toNanos();
		BookCompletion latest = null;
		while (System.nanoTime() < deadline) {
			MvcResult statusResult = mockMvc.perform(
					get("/api/v1/boss-runs/{runId}/book-generation", runId).session(session))
					.andExpect(status().isOk())
					.andReturn();
			JsonNode json = objectMapper.readTree(statusResult.getResponse().getContentAsString());
			latest = new BookCompletion(json.path("jobId").asLong(), json.path("bookId").asLong(),
					json.path("jobStatus").asText(), json.path("bookStatus").asText());
			if (AiJobStatus.SUCCESS.name().equals(latest.jobStatus())) {
				return latest;
			}
			Thread.sleep(100);
		}
		throw new AssertionError("Book generation did not complete: " + latest);
	}

	@Test
	void readonlyRoleCanSelectMaterialsButCannotWrite() throws Exception {
		// V7이 만든 SELECT 전용 계정(ISSUE-122)의 강제력을 실제 접속으로 확인한다.
		// 배포에서 ai-service가 쓰는 계정이므로, 읽기는 되고 쓰기는 DB가 거부해야 한다.
		try (java.sql.Connection connection = java.sql.DriverManager.getConnection(
				POSTGRES.getJdbcUrl(), "mvp_ai_readonly", READONLY_PASSWORD);
				java.sql.Statement statement = connection.createStatement()) {
			statement.executeQuery("select count(*) from boss_submissions").close();
			org.assertj.core.api.Assertions.assertThatThrownBy(() -> statement.executeUpdate(
					"insert into mood_categories (code, name_ko, is_active) values ('E2E_RO', '검증', true)"))
					.hasMessageContaining("permission denied");
		}
	}

	private long readLong(MvcResult result, String field) throws Exception {
		return objectMapper.readTree(result.getResponse().getContentAsString()).path(field).asLong();
	}

	private void seedWords() {
		int suffix = SEQUENCE.incrementAndGet();
		for (int index = 0; index < 5; index++) {
			wordRepository.save(Word.from(DictionarySense.builder()
					.word("real-e2e-" + suffix + "-" + index)
					.partOfSpeech("noun")
					.definition("Real infrastructure E2E word " + index)
					.build()));
		}
	}

	private static GenericContainer<?> createAiServiceContainer() {
		String imageName = System.getenv("AI_CONTRACT_TEST_IMAGE");
		if (imageName == null || imageName.isBlank()) {
			imageName = "c206-ai-contract-test:mq";
		}
		return new GenericContainer<>(DockerImageName.parse(imageName))
				.withNetwork(NETWORK)
				.withExposedPorts(AI_SERVICE_PORT)
				.withEnv("BOOK_MQ_ENABLED", "true")
				.withEnv("RABBITMQ_HOST", "rabbitmq")
				.withEnv("RABBITMQ_PORT", "5672")
				.withEnv("RABBITMQ_USER", RABBIT_USER)
				.withEnv("RABBITMQ_PASSWORD", RABBIT_PASSWORD)
				.withEnv("RABBITMQ_VHOST", "/")
				// 소비자는 재료를 DB에서 직접 읽는다(ISSUE-122). 컨테이너 기동 시점에는
				// Flyway(V7)가 아직 SELECT 전용 계정을 만들기 전이라 여기서는 슈퍼유저로
				// 접속하고, 전용 계정의 쓰기 거부는 마이그레이션 후 별도 테스트로 검증한다.
				.withEnv("BOOK_DB_HOST", "postgres")
				.withEnv("BOOK_DB_PORT", "5432")
				.withEnv("BOOK_DB_NAME", POSTGRES.getDatabaseName())
				.withEnv("BOOK_DB_USER", POSTGRES.getUsername())
				.withEnv("BOOK_DB_PASSWORD", POSTGRES.getPassword())
				.withCommand("python", "-m", "uvicorn", "tests.contract_server:application",
						"--host", "0.0.0.0", "--port", Integer.toString(AI_SERVICE_PORT))
				.waitingFor(Wait.forHttp("/health").forStatusCode(200))
				.withStartupTimeout(Duration.ofMinutes(3));
	}

	private record BookCompletion(long jobId, long bookId, String jobStatus, String bookStatus) {
	}
}
