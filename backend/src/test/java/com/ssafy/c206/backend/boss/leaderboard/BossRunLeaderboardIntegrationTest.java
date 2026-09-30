package com.ssafy.c206.backend.boss.leaderboard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.UnsupportedEncodingException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;

import com.ssafy.c206.backend.boss.domain.Boss;
import com.ssafy.c206.backend.boss.domain.BossRun;
import com.ssafy.c206.backend.boss.leaderboard.service.BossRunLeaderboardBroadcaster;
import com.ssafy.c206.backend.boss.repository.BossPhaseRepository;
import com.ssafy.c206.backend.boss.repository.BossRepository;
import com.ssafy.c206.backend.boss.repository.BossRunRepository;
import com.ssafy.c206.backend.boss.submission.repository.BossSubmissionRepository;
import com.ssafy.c206.backend.boss.submission.service.BossSubmissionService;
import com.ssafy.c206.backend.common.security.AuthenticatedUser;
import com.ssafy.c206.backend.common.time.ServiceClock;
import com.ssafy.c206.backend.integration.leaderboard.BossRunLeaderboardStore;
import com.ssafy.c206.backend.integration.leaderboard.UserDamageTotal;
import com.ssafy.c206.backend.sentence.domain.Sentence;
import com.ssafy.c206.backend.sentence.domain.SentenceVersion;
import com.ssafy.c206.backend.sentence.evaluation.domain.SentenceEvaluation;
import com.ssafy.c206.backend.sentence.evaluation.repository.SentenceEvaluationRepository;
import com.ssafy.c206.backend.sentence.repository.SentenceRepository;
import com.ssafy.c206.backend.sentence.repository.SentenceVersionRepository;
import com.ssafy.c206.backend.user.domain.User;
import com.ssafy.c206.backend.user.domain.UserStatus;
import com.ssafy.c206.backend.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/**
 * 제출 커밋 → 증분 반영 → 병합 주기 재집계 → 조회·SSE의 전체 경로를 검증한다.
 *
 * <p>정합성 모델의 핵심은 "증분은 다리, 재집계가 진실"이다. 어느 경로로 읽어도 결과가
 * {@code boss_submissions}의 사용자별 sum(final_damage)과 일치해야 한다.
 */
// 병합 주기를 사실상 멈춰 두고 broadcastDirtyRuns를 직접 호출한다. 스케줄이 살아 있으면
// 콜드 경로 검증 전에 재집계가 끼어들어 어떤 경로가 실행됐는지 비결정적이 된다.
@SpringBootTest(properties = "boss.leaderboard.broadcast-delay-ms=3600000")
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class BossRunLeaderboardIntegrationTest {

	private static final AtomicInteger SEQUENCE = new AtomicInteger();
	private static final String PASSWORD = "Sentence8!";

	@Autowired private WebApplicationContext webApplicationContext;
	@Autowired private FilterChainProxy springSecurityFilterChain;
	private MockMvc mockMvc;
	@Autowired private BossSubmissionService submissionService;
	@Autowired private BossRunLeaderboardStore leaderboardStore;
	@Autowired private BossRunLeaderboardBroadcaster broadcaster;
	@Autowired private BossSubmissionRepository submissionRepository;
	@Autowired private BossRepository bossRepository;
	@Autowired private BossRunRepository runRepository;
	@Autowired private BossPhaseRepository phaseRepository;
	@Autowired private UserRepository userRepository;
	@Autowired private SentenceRepository sentenceRepository;
	@Autowired private SentenceVersionRepository versionRepository;
	@Autowired private SentenceEvaluationRepository evaluationRepository;
	@Autowired private PasswordEncoder passwordEncoder;
	@Autowired private ServiceClock serviceClock;

	@BeforeEach
	void setUp() {
		mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
				.addFilters(springSecurityFilterChain).build();
	}

	@Test
	void aColdReadWarmsTheStoreAndMatchesTheDatabaseAggregation() throws Exception {
		BossRun run = createActiveRun(100_000);
		UserSession first = userSession("lb-cold-a");
		UserSession second = userSession("lb-cold-b");
		submit(first, run, 90);
		submit(first, run, 90);
		submit(second, run, 90);
		// 병합 주기를 멈춰 뒀으므로 이 시점의 스토어는 확실히 콜드다(증분은 콜드 스킵).
		assertThat(leaderboardStore.isWarmedUp(run.getId())).isFalse();

		MvcResult result = mockMvc.perform(
				get("/api/v1/boss-runs/{id}/leaderboard", run.getId()).session(second.session()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.bossRunId").value(run.getId()))
				.andExpect(jsonPath("$.entries.length()").value(2))
				.andExpect(jsonPath("$.me.rank").value(2))
				.andReturn();

		// 콜드 조회는 DB 집계로 응답하면서 스토어를 워밍업한다.
		assertThat(leaderboardStore.isWarmedUp(run.getId())).isTrue();
		assertThat(storeTotals(run.getId())).isEqualTo(databaseTotals(run.getId()));
		String body = result.getResponse().getContentAsString();
		assertThat(body).contains("\"rank\":1").contains("\"rank\":2");
	}

	@Test
	void incrementsOnAWarmedStoreStayConsistentWithTheAggregation() throws Exception {
		BossRun run = createActiveRun(100_000);
		UserSession user = userSession("lb-warm");
		submit(user, run, 90);
		warmUp(run, user);

		// 워밍업 이후의 제출은 AFTER_COMMIT 증분으로 즉시 스토어에 반영된다.
		submit(user, run, 90);

		assertThat(storeTotals(run.getId())).isEqualTo(databaseTotals(run.getId()));
	}

	@Test
	void theMergeCycleReaggregatesDirtyRunsAndBroadcastsALeaderboardEvent() throws Exception {
		BossRun run = createActiveRun(100_000);
		UserSession subscriber = userSession("lb-sse");
		MvcResult stream = mockMvc.perform(
				get("/api/v1/boss-runs/{id}/events", run.getId()).session(subscriber.session()))
				.andExpect(request().asyncStarted())
				.andExpect(status().isOk())
				.andReturn();

		submit(subscriber, run, 90);
		broadcaster.broadcastDirtyRuns();

		// 이벤트 이름과 data 줄이 별개 write라, 이름만 기다리면 data가 아직 안 쓰인 틈을 읽을 수 있다.
		awaitTrue(() -> content(stream).contains("\"totalDamage\":90"));
		String content = content(stream);
		assertThat(content).contains("event:leaderboard");
		assertThat(content).contains("\"totalDamage\":90");
		assertThat(content).contains("\"rank\":1");
		// leaderboard 이벤트는 SSE id(시퀀스)를 소모하지 않아야 한다. 소모하면 클라이언트가
		// hp 시퀀스 틈을 유실로 오인한다. 이 스트림의 id는 스냅숏(id:0)과 제출(id:1)뿐이어야 한다.
		assertThat(content.lines().filter(line -> line.startsWith("id:")).toList())
				.containsExactly("id:0", "id:1");
		// 병합 주기의 재집계가 스토어를 DB 집계와 일치시킨다.
		assertThat(storeTotals(run.getId())).isEqualTo(databaseTotals(run.getId()));
	}

	@Test
	void aRunWithoutSubmissionsReturnsAnEmptyLeaderboard() throws Exception {
		BossRun run = createActiveRun(100_000);
		UserSession user = userSession("lb-empty");

		mockMvc.perform(get("/api/v1/boss-runs/{id}/leaderboard", run.getId()).session(user.session()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.entries.length()").value(0))
				.andExpect(jsonPath("$.me").doesNotExist());
	}

	@Test
	void anonymousLeaderboardAccessIsUnauthorized() throws Exception {
		BossRun run = createActiveRun(100_000);
		mockMvc.perform(get("/api/v1/boss-runs/{id}/leaderboard", run.getId()))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void anUnknownRunIsNotFound() throws Exception {
		UserSession user = userSession("lb-404");
		mockMvc.perform(get("/api/v1/boss-runs/{id}/leaderboard", 999_999_999L).session(user.session()))
				.andExpect(status().isNotFound());
	}

	private void submit(UserSession user, BossRun run, int score) {
		submissionService.submit(user.principal(), run.getId(),
				createEvaluatedVersion(user.principal(), score).getId(), true);
	}

	/** 콜드 조회 한 번으로 스토어를 워밍업한다. */
	private void warmUp(BossRun run, UserSession user) throws Exception {
		mockMvc.perform(get("/api/v1/boss-runs/{id}/leaderboard", run.getId()).session(user.session()))
				.andExpect(status().isOk());
		assertThat(leaderboardStore.isWarmedUp(run.getId())).isTrue();
	}

	private List<UserDamageTotal> storeTotals(long bossRunId) {
		return leaderboardStore.top(bossRunId, 100);
	}

	private List<UserDamageTotal> databaseTotals(long bossRunId) {
		return submissionRepository.aggregateDamageTotals(bossRunId).stream()
				.map(row -> new UserDamageTotal(row.getUserId(), row.getTotalDamage()))
				.toList();
	}

	/** SSE 팬아웃이 전용 스레드에서 실행되므로 조건이 참이 될 때까지 최대 5초 기다린다. */
	private static void awaitTrue(BooleanSupplier condition) throws InterruptedException {
		long deadline = System.currentTimeMillis() + 5_000;
		while (!condition.getAsBoolean() && System.currentTimeMillis() < deadline) {
			Thread.sleep(50);
		}
	}

	private static String content(MvcResult stream) {
		try {
			return stream.getResponse().getContentAsString();
		} catch (UnsupportedEncodingException exception) {
			throw new IllegalStateException(exception);
		}
	}

	private BossRun createActiveRun(long hp) {
		Instant now = serviceClock.now();
		int suffix = SEQUENCE.incrementAndGet();
		Boss boss = bossRepository.save(Boss.create("Leaderboard Boss " + suffix, "Test-only boss", now));
		Instant startsAt = now.minusSeconds(60);
		BossRun run = BossRun.schedule(boss, startsAt, startsAt.plusSeconds(7_200), now);
		run.addPhase(hp, BigDecimal.ZERO, null, BigDecimal.ONE);
		run = runRepository.save(run);
		run.activate(now);
		phaseRepository.saveAll(run.getPhases());
		return runRepository.save(run);
	}

	private SentenceVersion createEvaluatedVersion(AuthenticatedUser principal, int score) {
		Instant now = serviceClock.now();
		User user = userRepository.findById(principal.userId()).orElseThrow();
		Sentence sentence = sentenceRepository.save(Sentence.create(user, now));
		SentenceVersion version = versionRepository.save(
				SentenceVersion.draft(sentence, 1, "리더보드 검증 문장 " + SEQUENCE.incrementAndGet(), now));
		version.startEvaluation(now);
		version.markEvaluated(now);
		version = versionRepository.save(version);
		evaluationRepository.save(SentenceEvaluation.passed(version, score, score, score, score, score,
				BigDecimal.valueOf(score), BigDecimal.ZERO, BigDecimal.valueOf(score), "ok", "fake", "v1", "v1", now));
		return version;
	}

	private UserSession userSession(String prefix) throws Exception {
		String suffix = prefix + '-' + SEQUENCE.incrementAndGet();
		String email = suffix + "@leaderboard.test";
		User user = userRepository.save(User.createFixture(email, passwordEncoder.encode(PASSWORD), suffix));
		MvcResult login = mockMvc.perform(post("/api/v1/auth/sessions").with(csrf()).contentType("application/json")
				.content("{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"}"))
				.andExpect(status().isOk()).andReturn();
		return new UserSession(new AuthenticatedUser(user.getId(), user.getEmail()),
				(MockHttpSession) login.getRequest().getSession(false));
	}

	private record UserSession(AuthenticatedUser principal, MockHttpSession session) { }
}
