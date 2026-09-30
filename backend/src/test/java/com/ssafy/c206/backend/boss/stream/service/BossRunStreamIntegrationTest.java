package com.ssafy.c206.backend.boss.stream.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.UnsupportedEncodingException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;

import com.ssafy.c206.backend.boss.domain.Boss;
import com.ssafy.c206.backend.boss.domain.BossRun;
import com.ssafy.c206.backend.boss.repository.BossPhaseRepository;
import com.ssafy.c206.backend.boss.repository.BossRepository;
import com.ssafy.c206.backend.boss.repository.BossRunRepository;
import com.ssafy.c206.backend.boss.submission.service.BossSubmissionPreparation;
import com.ssafy.c206.backend.boss.submission.service.BossSubmissionService;
import com.ssafy.c206.backend.boss.submission.service.BossSubmissionTransaction;
import com.ssafy.c206.backend.common.security.AuthenticatedUser;
import com.ssafy.c206.backend.common.time.ServiceClock;
import com.ssafy.c206.backend.integration.stream.BossRunEventStream;
import com.ssafy.c206.backend.integration.stream.BossRunHpStreamEvent;
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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.WebApplicationContext;

/**
 * 제출 커밋 → SSE 구독자 전파의 전체 경로를 검증한다. test 프로필은 인프로세스 채널을 쓰므로
 * 전파가 호출 스레드에서 동기적으로 일어나 별도 대기 없이 응답 본문을 검사할 수 있다.
 */
@SpringBootTest
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class BossRunStreamIntegrationTest {

	private static final AtomicInteger SEQUENCE = new AtomicInteger();
	private static final String PASSWORD = "Sentence8!";

	@Autowired private WebApplicationContext webApplicationContext;
	@Autowired private FilterChainProxy springSecurityFilterChain;
	private MockMvc mockMvc;
	@Autowired private BossSubmissionService submissionService;
	@Autowired private BossSubmissionTransaction transaction;
	@Autowired private BossRunStreamRegistry registry;
	@Autowired private BossRunEventStream eventStream;
	@Autowired private PlatformTransactionManager transactionManager;
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
	void aCommittedSubmissionReachesEverySubscriber() throws Exception {
		BossRun run = createActiveRun(100_000);
		UserSession subscriberA = userSession("stream-a");
		UserSession subscriberB = userSession("stream-b");

		MvcResult streamA = subscribe(run.getId(), subscriberA.session());
		MvcResult streamB = subscribe(run.getId(), subscriberB.session());
		assertThat(content(streamA)).contains("\"accumulatedDamage\":0");
		assertThat(content(streamB)).contains("\"accumulatedDamage\":0");

		submissionService.submit(subscriberA.principal(), run.getId(),
				createEvaluatedVersion(subscriberA.principal(), 90).getId(), true);

		// 팬아웃이 전용 스레드에서 실행되므로 도착을 기다린 뒤 검증한다.
		for (MvcResult stream : List.of(streamA, streamB)) {
			awaitTrue(() -> content(stream).contains("\"appliedDamage\":90"));
			String content = content(stream);
			assertThat(content).contains("event:hp");
			assertThat(content).contains("\"appliedDamage\":90");
			assertThat(content).contains("\"accumulatedDamage\":90");
			assertThat(content).contains("\"baseScore\":90");
			assertThat(content).contains("\"remainingHp\":" + (100_000 - 90));
		}
		assertThat(streamA.getResponse().getContentType()).startsWith("text/event-stream");
		assertThat(streamA.getResponse().getHeader("X-Accel-Buffering")).isEqualTo("no");
	}

	@Test
	void sequenceIdsIncreaseWithoutGapsAcrossSubmissions() throws Exception {
		BossRun run = createActiveRun(100_000);
		UserSession subscriber = userSession("stream-seq");
		MvcResult stream = subscribe(run.getId(), subscriber.session());

		submissionService.submit(subscriber.principal(), run.getId(),
				createEvaluatedVersion(subscriber.principal(), 90).getId(), true);
		submissionService.submit(subscriber.principal(), run.getId(),
				createEvaluatedVersion(subscriber.principal(), 90).getId(), true);

		awaitTrue(() -> content(stream).contains("id:2"));
		String content = content(stream);
		// 스냅숏(id:0) 뒤로 제출마다 1씩 증가해야 한다. 틈이 생기면 클라이언트가 유실로 오인한다.
		assertThat(content).contains("id:0");
		assertThat(content).contains("id:1");
		assertThat(content).contains("id:2");
	}

	@Test
	void aRolledBackSubmissionEmitsNothing() {
		BossRun run = createActiveRun(100_000);
		AuthenticatedUser user = createUser("stream-rollback");
		long versionId = createEvaluatedVersion(user, 90).getId();
		List<BossRunHpStreamEvent> received = new ArrayList<>();
		eventStream.addListener(event -> {
			if (event.bossRunId() == run.getId()) {
				received.add(event);
			}
		});
		BossSubmissionPreparation preparation = transaction.prepare(user.userId(), run.getId(), versionId, true,
				serviceClock.now());

		new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
			transaction.apply(preparation, serviceClock.now());
			status.setRollbackOnly();
		});

		assertThat(received).isEmpty();
	}

	@Test
	void clearingTheRunCompletesAndReleasesEverySubscriber() throws Exception {
		// 마지막 페이즈 HP가 한 번의 제출로 깨지는 회차. 클리어 이벤트가 구독 정리를 유발해야 한다.
		BossRun run = createActiveRun(50);
		UserSession subscriber = userSession("stream-clear");
		MvcResult stream = subscribe(run.getId(), subscriber.session());

		submissionService.submit(subscriber.principal(), run.getId(),
				createEvaluatedVersion(subscriber.principal(), 90).getId(), true);

		awaitTrue(() -> registry.connectionCount(run.getId()) == 0);
		assertThat(content(stream)).contains("\"runStatus\":\"CLEARED\"");
		assertThat(registry.connectionCount(run.getId())).isZero();
	}

	@Test
	void aSubscriberRegisteredAfterTheTerminalEventIsClosedImmediately() throws Exception {
		// 구독 검증(ACTIVE 확인)과 등록 사이에 회차가 종료되는 경합을 재현한다. 종료 브로드캐스트를
		// 놓친 채 등록된 연결이 타임아웃까지 하트비트만 소비하면 안 된다.
		BossRun run = createActiveRun(50);
		UserSession attacker = userSession("stream-late");
		submissionService.submit(attacker.principal(), run.getId(),
				createEvaluatedVersion(attacker.principal(), 90).getId(), true);
		awaitTrue(() -> registry.connectionCount(run.getId()) == 0);

		var lateEmitter = registry.createEmitter();
		registry.register(run.getId(), lateEmitter);

		// 종료된 회차로 등록된 연결은 즉시 완료되어 다음 브로드캐스트·하트비트 대상에서 빠진다.
		eventStream.publish(new BossRunHpStreamEvent(run.getId(), 99L, 1, 0L, 50L, 0L,
				BigDecimal.ZERO, "CLEARED"));
		awaitTrue(() -> registry.connectionCount(run.getId()) == 0);
		assertThat(registry.connectionCount(run.getId())).isZero();
	}

	@Test
	void deadConnectionsAreRemovedOnTheNextBroadcast() throws Exception {
		BossRun run = createActiveRun(100_000);
		var emitter = registry.createEmitter();
		registry.register(run.getId(), emitter);
		assertThat(registry.connectionCount(run.getId())).isOne();

		// 클라이언트가 끊긴 연결은 다음 전송이 실패하면서 레지스트리에서 빠져야 한다.
		// 여기서 빠지지 않으면 하트비트와 브로드캐스트가 죽은 연결로 계속 시도되는 누수다.
		emitter.complete();
		eventStream.publish(new BossRunHpStreamEvent(run.getId(), 99L, 1, 90_000L, 100_000L, 10_000L,
				BigDecimal.valueOf(84), "ACTIVE"));

		awaitTrue(() -> registry.connectionCount(run.getId()) == 0);
		assertThat(registry.connectionCount(run.getId())).isZero();
	}

	@Test
	void anonymousSubscriptionIsUnauthorized() throws Exception {
		BossRun run = createActiveRun(100_000);
		mockMvc.perform(get("/api/v1/boss-runs/{id}/events", run.getId()))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void subscribingToARunThatIsNotActiveIsRejectedAsConflict() throws Exception {
		// 시작 시각이 지난 SCHEDULED는 지연 활성화(ISSUE-200)가 ACTIVE로 바꿔 버리므로,
		// 진짜 SCHEDULED 케이스가 되려면 시작 시각이 미래여야 한다.
		BossRun scheduled = createScheduledRunStartingAt(serviceClock.now().plusSeconds(3_600));
		UserSession subscriber = userSession("stream-scheduled");
		mockMvc.perform(get("/api/v1/boss-runs/{id}/events", scheduled.getId()).session(subscriber.session()))
				.andExpect(status().isConflict());
	}

	private MvcResult subscribe(long bossRunId, MockHttpSession session) throws Exception {
		return mockMvc.perform(get("/api/v1/boss-runs/{id}/events", bossRunId).session(session))
				.andExpect(request().asyncStarted())
				.andExpect(status().isOk())
				.andReturn();
	}

	/** 팬아웃이 전용 스레드에서 실행되므로 조건이 참이 될 때까지 최대 5초 기다린다. */
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
		BossRun run = createScheduledRun(hp);
		run.activate(now);
		phaseRepository.saveAll(run.getPhases());
		return runRepository.save(run);
	}

	private BossRun createScheduledRun(long hp) {
		return createScheduledRun(hp, serviceClock.now().minusSeconds(60));
	}

	private BossRun createScheduledRunStartingAt(Instant startsAt) {
		return createScheduledRun(100_000, startsAt);
	}

	private BossRun createScheduledRun(long hp, Instant startsAt) {
		Instant now = serviceClock.now();
		int suffix = SEQUENCE.incrementAndGet();
		Boss boss = bossRepository.save(Boss.create("Stream Boss " + suffix, "Test-only boss", now));
		BossRun run = BossRun.schedule(boss, startsAt, startsAt.plusSeconds(7_200), now);
		run.addPhase(hp, BigDecimal.ZERO, null, BigDecimal.ONE);
		return runRepository.save(run);
	}

	private AuthenticatedUser createUser(String prefix) {
		int suffix = SEQUENCE.incrementAndGet();
		User user = userRepository.save(
				User.createFixture(prefix + suffix + "@stream.test", "hash", prefix + suffix, UserStatus.ACTIVE));
		return new AuthenticatedUser(user.getId(), user.getEmail());
	}

	private SentenceVersion createEvaluatedVersion(AuthenticatedUser principal, int score) {
		Instant now = serviceClock.now();
		User user = userRepository.findById(principal.userId()).orElseThrow();
		Sentence sentence = sentenceRepository.save(Sentence.create(user, now));
		SentenceVersion version = versionRepository.save(
				SentenceVersion.draft(sentence, 1, "스트림 검증 문장 " + SEQUENCE.incrementAndGet(), now));
		version.startEvaluation(now);
		version.markEvaluated(now);
		version = versionRepository.save(version);
		evaluationRepository.save(SentenceEvaluation.passed(version, score, score, score, score, score,
				BigDecimal.valueOf(score), BigDecimal.ZERO, BigDecimal.valueOf(score), "ok", "fake", "v1", "v1", now));
		return version;
	}

	private UserSession userSession(String prefix) throws Exception {
		String suffix = prefix + '-' + SEQUENCE.incrementAndGet();
		String email = suffix + "@stream.test";
		User user = userRepository.save(User.createFixture(email, passwordEncoder.encode(PASSWORD), suffix));
		MvcResult login = mockMvc.perform(post("/api/v1/auth/sessions").with(csrf()).contentType("application/json")
				.content("{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"}"))
				.andExpect(status().isOk()).andReturn();
		return new UserSession(new AuthenticatedUser(user.getId(), user.getEmail()),
				(MockHttpSession) login.getRequest().getSession(false));
	}

	private record UserSession(AuthenticatedUser principal, MockHttpSession session) { }
}
