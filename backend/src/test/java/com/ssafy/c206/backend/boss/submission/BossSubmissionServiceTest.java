package com.ssafy.c206.backend.boss.submission;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import com.ssafy.c206.backend.boss.domain.Boss;
import com.ssafy.c206.backend.boss.domain.BossPhase;
import com.ssafy.c206.backend.boss.domain.BossPhaseStatus;
import com.ssafy.c206.backend.boss.domain.BossRun;
import com.ssafy.c206.backend.boss.domain.BossRunStatus;
import com.ssafy.c206.backend.boss.repository.BossPhaseRepository;
import com.ssafy.c206.backend.boss.repository.BossRepository;
import com.ssafy.c206.backend.boss.repository.BossRunRepository;
import com.ssafy.c206.backend.boss.submission.repository.BossSubmissionDailyCounterRepository;
import com.ssafy.c206.backend.boss.submission.repository.BossSubmissionRepository;
import com.ssafy.c206.backend.boss.submission.service.BossSubmissionService;
import com.ssafy.c206.backend.common.api.DomainException;
import com.ssafy.c206.backend.common.time.ServiceClock;
import com.ssafy.c206.backend.integration.similarity.FakeSentenceSimilarityService;
import com.ssafy.c206.backend.sentence.domain.Sentence;
import com.ssafy.c206.backend.sentence.domain.SentenceVersion;
import com.ssafy.c206.backend.sentence.evaluation.domain.SentenceEvaluation;
import com.ssafy.c206.backend.sentence.evaluation.repository.SentenceEvaluationRepository;
import com.ssafy.c206.backend.sentence.repository.SentenceRepository;
import com.ssafy.c206.backend.sentence.repository.SentenceVersionRepository;
import com.ssafy.c206.backend.user.domain.User;
import com.ssafy.c206.backend.user.repository.UserRepository;
import com.ssafy.c206.backend.common.security.AuthenticatedUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class BossSubmissionServiceTest {

	private static final AtomicInteger SEQUENCE = new AtomicInteger();

	@Autowired private BossSubmissionService submissionService;
	@Autowired private FakeSentenceSimilarityService similarityService;
	@Autowired private BossRepository bossRepository;
	@Autowired private BossRunRepository runRepository;
	@Autowired private BossPhaseRepository phaseRepository;
	@Autowired private BossSubmissionRepository submissionRepository;
	@Autowired private BossSubmissionDailyCounterRepository counterRepository;
	@Autowired private UserRepository userRepository;
	@Autowired private SentenceRepository sentenceRepository;
	@Autowired private SentenceVersionRepository versionRepository;
	@Autowired private SentenceEvaluationRepository evaluationRepository;
	@Autowired private ServiceClock serviceClock;

	@AfterEach
	void clearSimilarity() {
		similarityService.clear();
	}

	@Test
	void concurrentFourSubmissionsAllowOnlyThreeAndKeepHpConsistent() throws Exception {
		BossRun run = createRun(1_000, 1);
		AuthenticatedUser user = createUser("limit");
		List<Long> versions = List.of(createEvaluatedVersion(user, 80).getId(), createEvaluatedVersion(user, 80).getId(),
				createEvaluatedVersion(user, 80).getId(), createEvaluatedVersion(user, 80).getId());
		var executor = Executors.newFixedThreadPool(4);
		CountDownLatch ready = new CountDownLatch(4);
		CountDownLatch start = new CountDownLatch(1);
		try {
			List<Future<Boolean>> results = new ArrayList<>();
			for (Long versionId : versions) results.add(executor.submit(submit(user, run.getId(), versionId, ready, start)));
			assertThat(ready.await(5, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
			start.countDown();
			int succeeded = 0;
			for (Future<Boolean> result : results) if (result.get()) succeeded++;
			assertThat(succeeded).isEqualTo(3);
			assertThat(submissionRepository.countByBossRunId(run.getId())).isEqualTo(3);
			assertThat(counterRepository.countByBossRunId(run.getId())).isEqualTo(1);
			assertThat(counterRepository.findByBossRunIdAndUserIdAndServiceDate(run.getId(), user.userId(), serviceClock.today()).orElseThrow().getSubmissionCount()).isEqualTo(3);
			BossPhase phase = phaseRepository.findAll().getFirst();
			assertThat(phase.getRemainingHp()).isEqualTo(760L);
			assertThat(runRepository.findById(run.getId()).orElseThrow().getAccumulatedDamage()).isEqualTo(240L);
		} finally {
			executor.shutdownNow();
		}
	}

	@Test
	void sameVersionConcurrentSubmissionIsPersistedOnce() throws Exception {
		BossRun run = createRun(1_000, 1);
		AuthenticatedUser user = createUser("duplicate");
		long versionId = createEvaluatedVersion(user, 80).getId();
		var executor = Executors.newFixedThreadPool(2);
		CountDownLatch ready = new CountDownLatch(2);
		CountDownLatch start = new CountDownLatch(1);
		try {
			Future<Boolean> first = executor.submit(submit(user, run.getId(), versionId, ready, start));
			Future<Boolean> second = executor.submit(submit(user, run.getId(), versionId, ready, start));
			assertThat(ready.await(5, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
			start.countDown();
			assertThat((first.get() ? 1 : 0) + (second.get() ? 1 : 0)).isOne();
			assertThat(submissionRepository.countByBossRunId(run.getId())).isOne();
			assertThat(counterRepository.findByBossRunIdAndUserIdAndServiceDate(run.getId(), user.userId(), serviceClock.today()).orElseThrow().getSubmissionCount()).isOne();
		} finally {
			executor.shutdownNow();
		}
	}

	@Test
	void clearsEachPhaseOnceThenClearsRunOnce() {
		BossRun run = createRun(100, 2);
		AuthenticatedUser user = createUser("clear");
		submissionService.submit(user, run.getId(), createEvaluatedVersion(user, 100).getId(), true);
		BossRun afterFirst = runRepository.findById(run.getId()).orElseThrow();
		assertThat(afterFirst.getStatus()).isEqualTo(BossRunStatus.ACTIVE);
		assertThat(afterFirst.getCurrentPhaseNo()).isEqualTo(2);
		assertThat(phaseRepository.findAll()).filteredOn(phase -> phase.getStatus() == BossPhaseStatus.CLEARED).hasSize(1);

		submissionService.submit(user, run.getId(), createEvaluatedVersion(user, 100).getId(), true);
		BossRun cleared = runRepository.findById(run.getId()).orElseThrow();
		assertThat(cleared.getStatus()).isEqualTo(BossRunStatus.CLEARED);
		assertThat(phaseRepository.findAll()).filteredOn(phase -> phase.getStatus() == BossPhaseStatus.CLEARED).hasSize(2);
		assertThatThrownBy(() -> submissionService.submit(user, run.getId(), createEvaluatedVersion(user, 100).getId(), true))
				.isInstanceOf(DomainException.class);
		assertThat(submissionRepository.countByBossRunId(run.getId())).isEqualTo(2);
	}

	@Test
	void rollbackOnSimilarityRejectionRestoresCounterSubmissionAndHp() {
		BossRun run = createRun(500, 1);
		AuthenticatedUser user = createUser("rollback");
		SentenceVersion version = createEvaluatedVersion(user, 80);
		similarityService.configure(user.userId(), run.getId(), version.getId(), true);

		assertThatThrownBy(() -> submissionService.submit(user, run.getId(), version.getId(), true))
				.isInstanceOf(DomainException.class);
		assertThat(counterRepository.countByBossRunId(run.getId())).isZero();
		assertThat(submissionRepository.countByBossRunId(run.getId())).isZero();
		assertThat(phaseRepository.findByBossRunIdOrderByPhaseNoAsc(run.getId()).getFirst().getRemainingHp()).isEqualTo(500L);
		assertThat(runRepository.findById(run.getId()).orElseThrow().getAccumulatedDamage()).isZero();
	}

	@Test
	void submissionPerformsLazyExpiryBeforeRejectingTheRequest() {
		BossRun run = createRun(500, 1, serviceClock.now().minusSeconds(1));
		AuthenticatedUser user = createUser("expired");
		assertThatThrownBy(() -> submissionService.submit(user, run.getId(), createEvaluatedVersion(user, 80).getId(), true))
				.isInstanceOf(DomainException.class);
		assertThat(runRepository.findById(run.getId()).orElseThrow().getStatus()).isEqualTo(BossRunStatus.ENDED_UNCLEARED);
		assertThat(submissionRepository.countByBossRunId(run.getId())).isZero();
	}

	private Callable<Boolean> submit(AuthenticatedUser user, Long runId, Long versionId) {
		return submit(user, runId, versionId, null, null);
	}

	private Callable<Boolean> submit(AuthenticatedUser user, Long runId, Long versionId, CountDownLatch ready,
			CountDownLatch start) {
		return () -> {
			try {
				if (ready != null) {
					ready.countDown();
					try {
						start.await(5, java.util.concurrent.TimeUnit.SECONDS);
					} catch (InterruptedException exception) {
						Thread.currentThread().interrupt();
						return false;
					}
				}
				submissionService.submit(user, runId, versionId, true);
				return true;
			} catch (DomainException exception) {
				return false;
			}
		};
	}

	private BossRun createRun(long hp, int phaseCount) {
		return createRun(hp, phaseCount, serviceClock.now().plusSeconds(3_600));
	}

	private BossRun createRun(long hp, int phaseCount, Instant endsAt) {
		Instant now = serviceClock.now();
		int suffix = SEQUENCE.incrementAndGet();
		Boss boss = bossRepository.save(Boss.create("Submission Boss " + suffix, "Test-only boss", now));
		BossRun run = BossRun.schedule(boss, now.minusSeconds(60), endsAt, now);
		for (int index = 0; index < phaseCount; index++) run.addPhase(hp, BigDecimal.ZERO, null, BigDecimal.ONE);
		run = runRepository.save(run);
		run.activate(now);
		phaseRepository.saveAll(run.getPhases());
		return runRepository.save(run);
	}

	private AuthenticatedUser createUser(String prefix) {
		int suffix = SEQUENCE.incrementAndGet();
		User user = userRepository.save(User.createFixture(prefix + suffix + "@submission.test", "hash", prefix + suffix));
		return new AuthenticatedUser(user.getId(), user.getEmail());
	}

	private SentenceVersion createEvaluatedVersion(AuthenticatedUser principal, int score) {
		Instant now = serviceClock.now();
		User user = userRepository.findById(principal.userId()).orElseThrow();
		Sentence sentence = sentenceRepository.save(Sentence.create(user, now));
		SentenceVersion version = versionRepository.save(SentenceVersion.draft(sentence, 1, "제출 검증 문장 " + SEQUENCE.incrementAndGet(), now));
		version.startEvaluation(now);
		version.markEvaluated(now);
		version = versionRepository.save(version);
		evaluationRepository.save(SentenceEvaluation.passed(version, score, score, score, score, score,
				BigDecimal.valueOf(score), BigDecimal.ZERO, BigDecimal.valueOf(score), "ok", "fake", "v1", "v1", now));
		return version;
	}
}
