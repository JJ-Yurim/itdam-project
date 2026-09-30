package com.ssafy.c206.backend.postgres;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import com.ssafy.c206.backend.book.domain.AiJobType;
import com.ssafy.c206.backend.book.domain.AiJobStatus;
import com.ssafy.c206.backend.book.domain.BookStatus;
import com.ssafy.c206.backend.book.repository.AiJobRepository;
import com.ssafy.c206.backend.book.repository.BookContentRepository;
import com.ssafy.c206.backend.book.repository.BookRepository;
import com.ssafy.c206.backend.book.service.BookGenerationPreparation;
import com.ssafy.c206.backend.book.service.BookGenerationPreparationService;
import com.ssafy.c206.backend.book.service.BookGenerationFailureService;
import com.ssafy.c206.backend.book.service.BookGenerationResultTransaction;
import com.ssafy.c206.backend.boss.api.AdminBossRunCreateRequest;
import com.ssafy.c206.backend.boss.api.AdminBossRunResponse;
import com.ssafy.c206.backend.boss.domain.Boss;
import com.ssafy.c206.backend.boss.domain.BossPhaseTemplate;
import com.ssafy.c206.backend.boss.domain.BossPhaseStatus;
import com.ssafy.c206.backend.boss.domain.BossRun;
import com.ssafy.c206.backend.boss.domain.BossRunStatus;
import com.ssafy.c206.backend.boss.repository.BossPhaseRepository;
import com.ssafy.c206.backend.boss.repository.BossPhaseTemplateRepository;
import com.ssafy.c206.backend.boss.repository.BossRepository;
import com.ssafy.c206.backend.boss.repository.BossRunRepository;
import com.ssafy.c206.backend.boss.service.BossFixture;
import com.ssafy.c206.backend.boss.service.BossRunAdminService;
import com.ssafy.c206.backend.boss.service.BossRunLifecycleService;
import com.ssafy.c206.backend.boss.image.domain.ImageAsset;
import com.ssafy.c206.backend.boss.image.domain.ImagePurpose;
import com.ssafy.c206.backend.boss.image.repository.ImageAssetRepository;
import com.ssafy.c206.backend.boss.submission.repository.BossSubmissionDailyCounterRepository;
import com.ssafy.c206.backend.boss.submission.repository.BossSubmissionRepository;
import com.ssafy.c206.backend.boss.submission.domain.BossSubmission;
import com.ssafy.c206.backend.boss.submission.service.DamageCalculation;
import com.ssafy.c206.backend.boss.submission.service.BossSubmissionResult;
import com.ssafy.c206.backend.boss.submission.service.BossSubmissionService;
import com.ssafy.c206.backend.common.api.DomainException;
import com.ssafy.c206.backend.common.api.ErrorCode;
import com.ssafy.c206.backend.common.time.ServiceClock;
import com.ssafy.c206.backend.dailyword.api.DailyWordResponse;
import com.ssafy.c206.backend.dailyword.repository.DailyWordSetItemRepository;
import com.ssafy.c206.backend.dailyword.repository.DailyWordSetRepository;
import com.ssafy.c206.backend.dailyword.service.DailyWordService;
import com.ssafy.c206.backend.integration.book.FakeBookGenerationRequester;
import com.ssafy.c206.backend.integration.book.BookGenerationResult;
import com.ssafy.c206.backend.sentence.api.SentenceVersionResponse;
import com.ssafy.c206.backend.sentence.domain.Sentence;
import com.ssafy.c206.backend.sentence.domain.SentenceVersion;
import com.ssafy.c206.backend.sentence.evaluation.domain.SentenceEvaluation;
import com.ssafy.c206.backend.sentence.evaluation.repository.SentenceEvaluationRepository;
import com.ssafy.c206.backend.sentence.repository.SentenceRepository;
import com.ssafy.c206.backend.sentence.repository.SentenceVersionRepository;
import com.ssafy.c206.backend.sentence.service.SentenceCommandService;
import com.ssafy.c206.backend.user.domain.User;
import com.ssafy.c206.backend.common.security.AuthenticatedUser;
import com.ssafy.c206.backend.user.repository.UserRepository;
import com.ssafy.c206.backend.user.service.UserFixture;
import com.ssafy.c206.backend.word.domain.Word;
import com.ssafy.c206.backend.word.etl.DictionarySense;
import com.ssafy.c206.backend.word.repository.WordRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

class PostgresConcurrencyIntegrationTest extends PostgresIntegrationTestSupport {

	private static final AtomicInteger SEQUENCE = new AtomicInteger();

	@Autowired private DailyWordService dailyWordService;
	@Autowired private DailyWordSetRepository dailyWordSetRepository;
	@Autowired private DailyWordSetItemRepository dailyWordSetItemRepository;
	@Autowired private WordRepository wordRepository;
	@Autowired private UserFixture userFixture;
	@Autowired private UserRepository userRepository;
	@Autowired private SentenceCommandService sentenceCommandService;
	@Autowired private SentenceVersionRepository sentenceVersionRepository;
	@Autowired private BossFixture bossFixture;
	@Autowired private BossRepository bossRepository;
	@Autowired private BossRunRepository bossRunRepository;
	@Autowired private BossPhaseRepository bossPhaseRepository;
	@Autowired private BossPhaseTemplateRepository bossPhaseTemplateRepository;
	@Autowired private ImageAssetRepository imageAssetRepository;
	@Autowired private BossRunAdminService bossRunAdminService;
	@Autowired private BossRunLifecycleService bossRunLifecycleService;
	@Autowired private BossSubmissionService bossSubmissionService;
	@Autowired private BossSubmissionRepository bossSubmissionRepository;
	@Autowired private BossSubmissionDailyCounterRepository bossSubmissionDailyCounterRepository;
	@Autowired private SentenceRepository sentenceRepository;
	@Autowired private SentenceEvaluationRepository sentenceEvaluationRepository;
	@Autowired private BookGenerationPreparationService bookGenerationPreparationService;
	@Autowired private BookRepository bookRepository;
	@Autowired private AiJobRepository aiJobRepository;
	@Autowired private FakeBookGenerationRequester requester;
	@Autowired private ServiceClock serviceClock;
	@Autowired private JdbcTemplate jdbcTemplate;
	@Autowired private BookGenerationResultTransaction bookGenerationResultTransaction;
	@Autowired private BookGenerationFailureService bookGenerationFailureService;
	@Autowired private BookContentRepository bookContentRepository;

	@AfterEach
	void clearRequester() {
		requester.clear();
	}

	@Test
	void dailyWordFirstIssuanceRaceCreatesExactlyOneSetAndFiveItems() throws Exception {
		User user = userFixture.createFixture(email("daily"), "hash", nickname("daily"));
		wordRepository.saveAllAndFlush(IntStream.range(0, 8).mapToObj(index ->
				Word.from(DictionarySense.builder().word("pg-word-" + SEQUENCE.incrementAndGet())
						.partOfSpeech("noun").definition("definition").build())).toList());

		List<DailyWordResponse> responses = executeTogether(8, () ->
				dailyWordService.getToday(new AuthenticatedUser(user.getId(), user.getEmail())));

		assertThat(responses).allSatisfy(response -> assertThat(response).isEqualTo(responses.getFirst()));
		assertThat(dailyWordSetRepository.count()).isEqualTo(1);
		assertThat(dailyWordSetItemRepository.count()).isEqualTo(5);
	}

	@Test
	void sentenceVersionRaceUsesPostgresPessimisticLockAndKeepsSequentialNumbers() throws Exception {
		User user = userFixture.createFixture(email("version"), "hash", nickname("version"));
		var first = sentenceCommandService.create(user.getId(), "initial draft");

		List<SentenceVersionResponse> created = executeTogether(2, () ->
				sentenceCommandService.createVersion(user.getId(), first.sentenceId(), "concurrent revision"));

		assertThat(created).extracting(SentenceVersionResponse::versionNo).containsExactlyInAnyOrder(2, 3);
		assertThat(sentenceVersionRepository.findBySentenceIdOrderByVersionNoAsc(first.sentenceId()))
				.extracting(SentenceVersion::getVersionNo).containsExactly(1, 2, 3);
	}

	@Test
	void clearedRunRaceCreatesOneBookAndOneGenerationJob() throws Exception {
		Instant now = serviceClock.now();
		BossRun run = bossFixture.createThreePhaseRun(now.minusSeconds(60), now.plusSeconds(3600), now);
		run.markCleared(now);
		run = bossRunRepository.saveAndFlush(run);
		Long runId = run.getId();
		AuthenticatedUser author = createUser("clear-race-material");
		SentenceVersion version = createEvaluatedVersion(author, 90);
		var phase = bossPhaseRepository.findDetailsByBossRunIdIn(List.of(runId)).getFirst();
		User user = userRepository.findById(author.userId()).orElseThrow();
		DamageCalculation damage = new DamageCalculation(new BigDecimal("90"), BigDecimal.ZERO,
				new BigDecimal("90"), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ONE, 90L);
		bossSubmissionRepository.saveAndFlush(BossSubmission.create(run, phase, user, version,
				serviceClock.today(), damage, now));

		List<BookGenerationPreparation> preparations = executeTogether(4, () ->
				bookGenerationPreparationService.prepareAfterClear(runId));
		preparations.forEach(bookGenerationPreparationService::requestAfterCommit);

		assertThat(bookRepository.findByBossRunId(runId)).isPresent();
		assertThat(aiJobRepository.findByBossRunIdAndJobType(runId,
				AiJobType.BOOK_GENERATION)).isPresent();
		// 준비 시점에는 모두 미발행 상태를 관찰할 수 있지만, 실제 발행은 AiJob 잠금 안에서
		// 다시 확인하므로 한 번만 수행된다.
		assertThat(preparations).allMatch(BookGenerationPreparation::requestRequired);
		assertThat(requester.requests()).hasSize(1);
	}

	@RepeatedTest(5)
	void resultAndTimeoutRaceLeavesOneConsistentTerminalState() throws Exception {
		Instant now = serviceClock.now();
		BossRun run = bossFixture.createThreePhaseRun(now.minusSeconds(60), now.plusSeconds(3600), now);
		run.markCleared(now);
		run = bossRunRepository.saveAndFlush(run);
		bookGenerationPreparationService.prepareAfterClear(run.getId());
		var job = aiJobRepository.findByBossRunIdAndJobType(run.getId(), AiJobType.BOOK_GENERATION).orElseThrow();
		List<BookGenerationResult.Content> contents = List.of(
				new BookGenerationResult.Content(1, "AI_GENERATED", "동시성 검증 본문", null));
		BookGenerationResult success = BookGenerationResult.success(job.getId(), job.getCorrelationId(),
				contents, contents.getFirst().content(), "테스트용 요약입니다.",
				List.of(), List.of(), "test-model", "test-prompt");

		executeTogether(2, index -> {
			try {
				if (index == 0) {
					bookGenerationResultTransaction.apply(success);
				} else {
					bookGenerationFailureService.failIfTimedOut(job.getId(), Duration.ZERO);
				}
				return true;
			} catch (DomainException exception) {
				assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.CONFLICT);
				return false;
			}
		});

		var persistedJob = aiJobRepository.findById(job.getId()).orElseThrow();
		var book = bookRepository.findByBossRunId(run.getId()).orElseThrow();
		if (persistedJob.getStatus() == AiJobStatus.SUCCESS) {
			assertThat(book.getStatus()).isEqualTo(BookStatus.COMPLETED);
			assertThat(bookContentRepository.countByBookId(book.getId())).isOne();
		} else {
			assertThat(persistedJob.getStatus()).isEqualTo(AiJobStatus.FAILED);
			assertThat(book.getStatus()).isEqualTo(BookStatus.FAILED);
			assertThat(bookContentRepository.countByBookId(book.getId())).isZero();
		}
	}

	@RepeatedTest(5)
	void phaseBoundaryRaceClearsFirstPhaseOnceAndAppliesNextSubmissionToSecondPhase() throws Exception {
		BossRun run = createActiveRun(80L, 1_000L);
		AuthenticatedUser user = createUser("phase-boundary");
		List<Long> versionIds = List.of(createEvaluatedVersion(user, 80).getId(),
				createEvaluatedVersion(user, 80).getId());

		List<SubmissionAttempt> attempts = executeTogether(2,
				index -> submitAttempt(user, run.getId(), versionIds.get(index)));

		assertThat(attempts).allMatch(SubmissionAttempt::succeeded);
		assertThat(attempts).extracting(attempt -> attempt.result().bossPhaseNo())
				.containsExactlyInAnyOrder(1, 2);
		assertThat(attempts).extracting(attempt -> attempt.result().finalDamage())
				.containsOnly(80L);

		BossRun persistedRun = bossRunRepository.findById(run.getId()).orElseThrow();
		assertThat(persistedRun.getStatus()).isEqualTo(BossRunStatus.ACTIVE);
		assertThat(persistedRun.getCurrentPhaseNo()).isEqualTo(2);
		assertThat(persistedRun.getAccumulatedDamage()).isEqualTo(160L);
		assertThat(bossPhaseRepository.findByBossRunIdOrderByPhaseNoAsc(run.getId()))
				.satisfiesExactly(
						phase -> {
							assertThat(phase.getStatus()).isEqualTo(BossPhaseStatus.CLEARED);
							assertThat(phase.getRemainingHp()).isZero();
						},
						phase -> {
							assertThat(phase.getStatus()).isEqualTo(BossPhaseStatus.ACTIVE);
							assertThat(phase.getRemainingHp()).isEqualTo(920L);
						});
		assertThat(bossSubmissionRepository.countByBossRunId(run.getId())).isEqualTo(2);
	}

	@Test
	void sameSentenceVersionRacePersistsOneSubmissionAndOneDamage() throws Exception {
		BossRun run = createActiveRun(1_000L);
		AuthenticatedUser user = createUser("same-version");
		Long versionId = createEvaluatedVersion(user, 80).getId();

		List<SubmissionAttempt> attempts = executeTogether(2,
				ignored -> submitAttempt(user, run.getId(), versionId));

		assertThat(attempts).filteredOn(SubmissionAttempt::succeeded).hasSize(1);
		assertThat(attempts).filteredOn(attempt -> !attempt.succeeded())
				.extracting(SubmissionAttempt::errorCode).containsExactly(ErrorCode.CONFLICT);
		assertThat(bossSubmissionRepository.countByBossRunId(run.getId())).isOne();
		assertThat(bossSubmissionDailyCounterRepository
				.findByBossRunIdAndUserIdAndServiceDate(run.getId(), user.userId(), serviceClock.today())
				.orElseThrow().getSubmissionCount()).isOne();
		assertThat(bossRunRepository.findById(run.getId()).orElseThrow().getAccumulatedDamage()).isEqualTo(80L);
		assertThat(bossPhaseRepository.findByBossRunIdOrderByPhaseNoAsc(run.getId()).getFirst().getRemainingHp())
				.isEqualTo(920L);
	}

	@Test
	void dailyLimitRaceAcceptsThreeSubmissionsAndKeepsCounterAndHpConsistent() throws Exception {
		BossRun run = createActiveRun(1_000L);
		AuthenticatedUser user = createUser("daily-limit");
		List<Long> versionIds = IntStream.range(0, 4)
				.mapToObj(ignored -> createEvaluatedVersion(user, 80).getId()).toList();

		List<SubmissionAttempt> attempts = executeTogether(4,
				index -> submitAttempt(user, run.getId(), versionIds.get(index)));

		assertThat(attempts).filteredOn(SubmissionAttempt::succeeded).hasSize(3);
		assertThat(attempts).filteredOn(attempt -> !attempt.succeeded())
				.extracting(SubmissionAttempt::errorCode).containsExactly(ErrorCode.RATE_LIMITED);
		assertThat(bossSubmissionRepository.countByBossRunId(run.getId())).isEqualTo(3);
		assertThat(bossSubmissionDailyCounterRepository
				.findByBossRunIdAndUserIdAndServiceDate(run.getId(), user.userId(), serviceClock.today())
				.orElseThrow().getSubmissionCount()).isEqualTo(3);
		assertThat(bossRunRepository.findById(run.getId()).orElseThrow().getAccumulatedDamage()).isEqualTo(240L);
		assertThat(bossPhaseRepository.findByBossRunIdOrderByPhaseNoAsc(run.getId()).getFirst().getRemainingHp())
				.isEqualTo(760L);
	}

	@Test
	void finalPhaseRaceClearsRunOnceAndCreatesOneBookGenerationRequest() throws Exception {
		BossRun run = createActiveRun(80L);
		AuthenticatedUser user = createUser("final-clear");
		List<Long> versionIds = List.of(createEvaluatedVersion(user, 80).getId(),
				createEvaluatedVersion(user, 80).getId());

		List<SubmissionAttempt> attempts = executeTogether(2,
				index -> submitAttempt(user, run.getId(), versionIds.get(index)));

		assertThat(attempts).filteredOn(SubmissionAttempt::succeeded).hasSize(1);
		assertThat(attempts).filteredOn(attempt -> !attempt.succeeded())
				.extracting(SubmissionAttempt::errorCode).containsExactly(ErrorCode.CONFLICT);
		assertThat(bossRunRepository.findById(run.getId()).orElseThrow().getStatus())
				.isEqualTo(BossRunStatus.CLEARED);
		assertThat(bossSubmissionRepository.countByBossRunId(run.getId())).isOne();
		assertThat(bookRepository.findByBossRunId(run.getId())).isPresent();
		assertThat(aiJobRepository.findByBossRunIdAndJobType(run.getId(),
				AiJobType.BOOK_GENERATION)).isPresent();
		assertThat(requester.requests()).hasSize(1);
	}

	@RepeatedTest(5)
	void activeRunCreationRaceAllowsExactlyOneWinnerForTheSameBoss() throws Exception {
		Instant now = serviceClock.now();
		Boss boss = bossRepository.saveAndFlush(Boss.create(
				"Concurrent active-run boss " + SEQUENCE.incrementAndGet(), "Unique-index regression fixture.", now));
		User owner = userFixture.createFixture(email("run-owner"), "hash", nickname("run-owner"));
		AuthenticatedUser principal = new AuthenticatedUser(owner.getId(), owner.getEmail());
		ImageAsset image = ImageAsset.temporary("boss-phases/concurrency-" + SEQUENCE.incrementAndGet() + ".png",
				"image/png", 10, ImagePurpose.BOSS_PHASE, owner, now);
		image.activate();
		image = imageAssetRepository.saveAndFlush(image);
		bossPhaseTemplateRepository.saveAndFlush(BossPhaseTemplate.of(boss, 1, 500, BigDecimal.ZERO, null,
				BigDecimal.ONE, image));
		AdminBossRunCreateRequest request = new AdminBossRunCreateRequest(
				boss.getId(), null, now.plusSeconds(3_600), true,
				List.of(new AdminBossRunCreateRequest.PhaseEntry(
						500L, BigDecimal.ZERO, null, BigDecimal.ONE, image.getId())));

		List<RunCreationAttempt> attempts = executeTogether(8,
				ignored -> createRunAttempt(principal, request));

		assertThat(attempts).filteredOn(RunCreationAttempt::succeeded).hasSize(1);
		assertThat(attempts).filteredOn(attempt -> !attempt.succeeded())
				.extracting(RunCreationAttempt::errorCode).containsOnly(ErrorCode.CONFLICT);
		assertThat(jdbcTemplate.queryForObject("""
				select count(*) from boss_runs where boss_id = ? and status = 'ACTIVE'
				""", Long.class, boss.getId())).isOne();
	}

	@Test
	void lazyActivationHonoursTheSingleActiveRunIndexInsteadOfFailingTheQueryThatTriggeredIt() {
		// 부분 유니크 인덱스는 PostgreSQL에만 있어 H2 단위 테스트로는 확인되지 않는다. 사전 확인이
		// 없으면 두 번째 회차에서 제약 위반이 터지고, 이 전이를 곁다리로 수행하던 조회 요청까지
		// 함께 실패한다. 예정 회차 하나 때문에 보스 목록이 통째로 500이 나면 안 된다.
		Instant now = serviceClock.now();
		Boss boss = bossRepository.saveAndFlush(Boss.create(
				"Lazy activation boss " + SEQUENCE.incrementAndGet(), "Single-active-index fixture.", now));
		BossRun earlier = bossFixture.scheduleThreePhaseRunFor(boss, now.minusSeconds(600), now.plusSeconds(3_600), now);
		BossRun later = bossFixture.scheduleThreePhaseRunFor(boss, now.minusSeconds(60), now.plusSeconds(7_200), now);

		bossRunLifecycleService.syncAllRunStates(serviceClock.now());

		assertThat(bossRunRepository.findById(earlier.getId()).orElseThrow().getStatus())
				.isEqualTo(BossRunStatus.ACTIVE);
		assertThat(bossRunRepository.findById(later.getId()).orElseThrow().getStatus())
				.isEqualTo(BossRunStatus.SCHEDULED);
		assertThat(jdbcTemplate.queryForObject("""
				select count(*) from boss_runs where boss_id = ? and status = 'ACTIVE'
				""", Long.class, boss.getId())).isOne();
	}

	@RepeatedTest(5)
	void concurrentLazyActivationOfTheSameRunLeavesExactlyOneActiveRun() throws Exception {
		// 여러 사용자가 동시에 보스 목록을 열면 지연 활성화도 동시에 돈다. 행 잠금을 잡은 뒤
		// 상태를 다시 확인하지 않으면 두 요청이 같은 회차를 활성화하려다 하나가 500으로 끝난다.
		Instant now = serviceClock.now();
		Boss boss = bossRepository.saveAndFlush(Boss.create(
				"Concurrent lazy activation boss " + SEQUENCE.incrementAndGet(), "Lazy activation race fixture.", now));
		BossRun run = bossFixture.scheduleThreePhaseRunFor(boss, now.minusSeconds(60), now.plusSeconds(3_600), now);

		List<Boolean> outcomes = executeTogether(6, ignored -> {
			bossRunLifecycleService.syncAllRunStates(serviceClock.now());
			return true;
		});

		assertThat(outcomes).containsOnly(true);
		assertThat(bossRunRepository.findById(run.getId()).orElseThrow().getStatus()).isEqualTo(BossRunStatus.ACTIVE);
		assertThat(bossPhaseRepository.findByBossRunIdOrderByPhaseNoAsc(run.getId()).getFirst().getStatus())
				.isEqualTo(BossPhaseStatus.ACTIVE);
		assertThat(jdbcTemplate.queryForObject("""
				select count(*) from boss_runs where boss_id = ? and status = 'ACTIVE'
				""", Long.class, boss.getId())).isOne();
	}

	@RepeatedTest(5)
	void concurrentLazyActivationOfTwoRunsOfTheSameBossLeavesExactlyOneActiveRun() throws Exception {
		// 유일 제약은 보스 단위인데 회차 행 잠금은 회차 단위다. 목록 경로가 이른 회차를, 상세·제출
		// 경로가 다른 회차를 동시에 올리면 서로 다른 행을 잠근 채 둘 다 "진행 중 없음"으로 판단해
		// 제약 위반에 부딪힌다. 조회에 얹혀 도는 전이라 그 위반은 조회 요청의 500이 된다.
		// 앞의 같은-회차 경합 테스트로는 덮이지 않는 경로다.
		Instant now = serviceClock.now();
		Boss boss = bossRepository.saveAndFlush(Boss.create(
				"Cross-path lazy activation boss " + SEQUENCE.incrementAndGet(), "Boss-scoped lock fixture.", now));
		BossRun earlier = bossFixture.scheduleThreePhaseRunFor(boss, now.minusSeconds(600), now.plusSeconds(3_600), now);
		BossRun later = bossFixture.scheduleThreePhaseRunFor(boss, now.minusSeconds(60), now.plusSeconds(7_200), now);

		List<Boolean> outcomes = executeTogether(6, index -> {
			if (index % 2 == 0) {
				bossRunLifecycleService.syncAllRunStates(serviceClock.now());
			} else {
				bossRunLifecycleService.syncStateIfNeeded(later.getId(), serviceClock.now());
			}
			return true;
		});

		assertThat(outcomes).containsOnly(true);
		assertThat(jdbcTemplate.queryForObject("""
				select count(*) from boss_runs where boss_id = ? and status = 'ACTIVE'
				""", Long.class, boss.getId())).isOne();
		// 어느 쪽이 이겼든 상관없다. 두 경로의 우선순위 규칙이 다르므로 승자는 경합 결과에 달렸다.
		assertThat(List.of(bossRunRepository.findById(earlier.getId()).orElseThrow().getStatus(),
				bossRunRepository.findById(later.getId()).orElseThrow().getStatus()))
				.containsExactlyInAnyOrder(BossRunStatus.ACTIVE, BossRunStatus.SCHEDULED);
	}

	@Test
	void concurrentTemplateReplacementKeepsOneCompleteFinalList() throws Exception {
		Instant now = serviceClock.now();
		Boss boss = bossRepository.saveAndFlush(Boss.create(
				"Concurrent template boss " + SEQUENCE.incrementAndGet(), "Template lock fixture.", now));
		User owner = userFixture.createFixture(email("template-owner"), "hash", nickname("template-owner"));
		AuthenticatedUser principal = new AuthenticatedUser(owner.getId(), owner.getEmail());
		ImageAsset a1 = temporaryImage(owner, "a1", now);
		ImageAsset a2 = temporaryImage(owner, "a2", now);
		ImageAsset b1 = temporaryImage(owner, "b1", now);
		ImageAsset b2 = temporaryImage(owner, "b2", now);
		AdminBossRunCreateRequest requestA = scheduledRequest(boss.getId(), now, a1.getId(), a2.getId(), 100L);
		AdminBossRunCreateRequest requestB = scheduledRequest(boss.getId(), now, b1.getId(), b2.getId(), 200L);

		List<RunCreationAttempt> attempts = executeTogether(2,
				index -> createRunAttempt(principal, index == 0 ? requestA : requestB));

		assertThat(attempts).allMatch(RunCreationAttempt::succeeded);
		List<Long> finalImages = bossPhaseTemplateRepository.findByBossIdOrderByPhaseNo(boss.getId()).stream()
				.map(template -> template.getImageAsset().getId()).toList();
		assertThat(finalImages.equals(List.of(a1.getId(), a2.getId()))
				|| finalImages.equals(List.of(b1.getId(), b2.getId()))).isTrue();
	}

	private <T> List<T> executeTogether(int callers, ThrowingSupplier<T> action) throws Exception {
		return executeTogether(callers, ignored -> action.get());
	}

	private <T> List<T> executeTogether(int callers, IndexedThrowingSupplier<T> action) throws Exception {
		ExecutorService executor = Executors.newFixedThreadPool(callers);
		CountDownLatch ready = new CountDownLatch(callers);
		CountDownLatch start = new CountDownLatch(1);
		try {
			List<Future<T>> futures = IntStream.range(0, callers).mapToObj(index -> executor.submit(() -> {
				ready.countDown();
				if (!start.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("race did not start");
				return action.get(index);
			})).toList();
			assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
			start.countDown();
			return futures.stream().map(this::get).toList();
		} finally {
			executor.shutdownNow();
		}
	}

	private BossRun createActiveRun(long... phaseHp) {
		Instant now = serviceClock.now();
		Boss boss = bossRepository.saveAndFlush(Boss.create(
				"Postgres concurrency boss " + SEQUENCE.incrementAndGet(), "Concurrency regression fixture.", now));
		BossRun run = BossRun.schedule(boss, now.minusSeconds(60), now.plusSeconds(3_600), now);
		for (long hp : phaseHp) {
			run.addPhase(hp, BigDecimal.ZERO, null, BigDecimal.ONE);
		}
		run.activate(now);
		return bossRunRepository.saveAndFlush(run);
	}

	private ImageAsset temporaryImage(User owner, String label, Instant now) {
		return imageAssetRepository.saveAndFlush(ImageAsset.temporary(
				"boss-phases/" + label + "-" + SEQUENCE.incrementAndGet() + ".png", "image/png", 10,
				ImagePurpose.BOSS_PHASE, owner, now));
	}

	private AdminBossRunCreateRequest scheduledRequest(Long bossId, Instant now, Long firstImageId,
			Long secondImageId, long hp) {
		return new AdminBossRunCreateRequest(bossId, null, now.plusSeconds(3600), false, List.of(
				new AdminBossRunCreateRequest.PhaseEntry(hp, BigDecimal.ZERO, null, BigDecimal.ONE, firstImageId),
				new AdminBossRunCreateRequest.PhaseEntry(hp + 1, BigDecimal.ZERO, null, BigDecimal.ONE, secondImageId)));
	}

	private AuthenticatedUser createUser(String prefix) {
		User user = userFixture.createFixture(email(prefix), "hash", nickname(prefix));
		return new AuthenticatedUser(user.getId(), user.getEmail());
	}

	private SentenceVersion createEvaluatedVersion(AuthenticatedUser principal, int attack) {
		Instant now = serviceClock.now();
		User user = userRepository.findById(principal.userId()).orElseThrow();
		Sentence sentence = sentenceRepository.saveAndFlush(Sentence.create(user, now));
		SentenceVersion version = SentenceVersion.draft(sentence, 1,
				"Postgres concurrent submission " + SEQUENCE.incrementAndGet(), now);
		version.startEvaluation(now);
		version.markEvaluated(now);
		version = sentenceVersionRepository.saveAndFlush(version);
		sentenceEvaluationRepository.saveAndFlush(SentenceEvaluation.passed(version,
				attack, attack, attack, attack, attack, BigDecimal.valueOf(attack), BigDecimal.ZERO,
				BigDecimal.valueOf(attack), "ok", "fake", "v1", "v1", now));
		return version;
	}

	private SubmissionAttempt submitAttempt(AuthenticatedUser user, Long runId, Long versionId) {
		try {
			return SubmissionAttempt.success(bossSubmissionService.submit(user, runId, versionId, true));
		} catch (DomainException exception) {
			return SubmissionAttempt.failure(exception.getErrorCode());
		}
	}

	private RunCreationAttempt createRunAttempt(AuthenticatedUser principal, AdminBossRunCreateRequest request) {
		try {
			return RunCreationAttempt.success(bossRunAdminService.create(principal, request));
		} catch (DomainException exception) {
			return RunCreationAttempt.failure(exception.getErrorCode());
		}
	}

	private <T> T get(Future<T> future) {
		try { return future.get(20, TimeUnit.SECONDS); }
		catch (Exception exception) { throw new AssertionError("concurrent operation failed", exception); }
	}

	private static String email(String prefix) { return prefix + "-" + SEQUENCE.incrementAndGet() + "@postgres.test"; }
	private static String nickname(String prefix) { return prefix + "-" + SEQUENCE.incrementAndGet(); }

	@FunctionalInterface
	private interface ThrowingSupplier<T> { T get() throws Exception; }

	@FunctionalInterface
	private interface IndexedThrowingSupplier<T> { T get(int index) throws Exception; }

	private record SubmissionAttempt(BossSubmissionResult result, ErrorCode errorCode) {
		static SubmissionAttempt success(BossSubmissionResult result) {
			return new SubmissionAttempt(result, null);
		}

		static SubmissionAttempt failure(ErrorCode errorCode) {
			return new SubmissionAttempt(null, errorCode);
		}

		boolean succeeded() {
			return result != null;
		}
	}

	private record RunCreationAttempt(AdminBossRunResponse result, ErrorCode errorCode) {
		static RunCreationAttempt success(AdminBossRunResponse result) {
			return new RunCreationAttempt(result, null);
		}

		static RunCreationAttempt failure(ErrorCode errorCode) {
			return new RunCreationAttempt(null, errorCode);
		}

		boolean succeeded() {
			return result != null;
		}
	}
}
