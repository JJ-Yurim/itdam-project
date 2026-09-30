package com.ssafy.c206.backend.boss.submission;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;

import com.ssafy.c206.backend.boss.domain.Boss;
import com.ssafy.c206.backend.boss.domain.BossRun;
import com.ssafy.c206.backend.boss.domain.BossRunStatus;
import com.ssafy.c206.backend.boss.repository.BossPhaseRepository;
import com.ssafy.c206.backend.boss.repository.BossRepository;
import com.ssafy.c206.backend.boss.repository.BossRunRepository;
import com.ssafy.c206.backend.boss.submission.repository.BossSubmissionDailyCounterRepository;
import com.ssafy.c206.backend.boss.submission.repository.BossSubmissionRepository;
import com.ssafy.c206.backend.boss.submission.service.BossSubmissionService;
import com.ssafy.c206.backend.common.api.DomainException;
import com.ssafy.c206.backend.common.api.ErrorCode;
import com.ssafy.c206.backend.common.time.ServiceClock;
import com.ssafy.c206.backend.sentence.domain.Sentence;
import com.ssafy.c206.backend.sentence.domain.SentenceVersion;
import com.ssafy.c206.backend.sentence.evaluation.domain.SentenceEvaluation;
import com.ssafy.c206.backend.sentence.evaluation.repository.SentenceEvaluationRepository;
import com.ssafy.c206.backend.sentence.repository.SentenceRepository;
import com.ssafy.c206.backend.sentence.repository.SentenceVersionRepository;
import com.ssafy.c206.backend.user.domain.User;
import com.ssafy.c206.backend.user.domain.UserStatus;
import com.ssafy.c206.backend.user.repository.UserRepository;
import com.ssafy.c206.backend.common.security.AuthenticatedUser;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

/**
 * BossSubmissionTransaction의 정책 거부 경로를 현재 동작 그대로 고정하는 특성화 테스트.
 * 리팩터링(-64) 시 거부 사유별 오류 코드와 부수 효과 부재가 유지되어야 한다.
 */
@SpringBootTest
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class BossSubmissionPolicyTest {

	private static final AtomicInteger SEQUENCE = new AtomicInteger();

	@Autowired private BossSubmissionService submissionService;
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

	@Test
	void submissionWithoutConsentIsRejectedBeforeAnyWrite() {
		BossRun run = createActiveRun(500);
		AuthenticatedUser user = createUser("consent", UserStatus.ACTIVE);
		long versionId = createEvaluatedVersion(user, 80).getId();

		assertThatThrownBy(() -> submissionService.submit(user, run.getId(), versionId, false))
				.isInstanceOfSatisfying(DomainException.class,
						exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.POLICY_REJECTED));
		assertThat(submissionRepository.countByBossRunId(run.getId())).isZero();
		assertThat(counterRepository.countByBossRunId(run.getId())).isZero();
	}

	@Test
	void scheduledRunRejectsSubmissionAsConflict() {
		BossRun run = createScheduledRun(500);
		AuthenticatedUser user = createUser("scheduled", UserStatus.ACTIVE);
		long versionId = createEvaluatedVersion(user, 80).getId();

		assertThatThrownBy(() -> submissionService.submit(user, run.getId(), versionId, true))
				.isInstanceOfSatisfying(DomainException.class,
						exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.CONFLICT));
	}

	@Test
	void scheduledRunWhoseStartTimePassedAcceptsSubmissionInsteadOfRejectingIt() {
		// 상태를 맞춰 주는 조회가 먼저 일어나야만 제출할 수 있다면, 시작 시각이 지난 레이드에
		// 링크로 바로 들어온 사용자는 이유 없이 막힌다. 제출 경로도 스스로 상태를 맞춘다.
		BossRun run = createScheduledRun(500, serviceClock.now().minusSeconds(60));
		AuthenticatedUser user = createUser("started", UserStatus.ACTIVE);
		long versionId = createEvaluatedVersion(user, 80).getId();

		submissionService.submit(user, run.getId(), versionId, true);

		assertThat(runRepository.findById(run.getId()).orElseThrow().getStatus()).isEqualTo(BossRunStatus.ACTIVE);
		assertThat(submissionRepository.countByBossRunId(run.getId())).isOne();
	}

	@Test
	void inactiveUserIsForbidden() {
		BossRun run = createActiveRun(500);
		AuthenticatedUser activeAuthor = createUser("author", UserStatus.ACTIVE);
		long versionId = createEvaluatedVersion(activeAuthor, 80).getId();
		User inactive = userRepository.save(User.createFixture(
				"inactive" + SEQUENCE.incrementAndGet() + "@policy.test", "hash",
				"inactive" + SEQUENCE.get(), UserStatus.INACTIVE));
		AuthenticatedUser inactivePrincipal = new AuthenticatedUser(inactive.getId(), inactive.getEmail());

		assertThatThrownBy(() -> submissionService.submit(inactivePrincipal, run.getId(), versionId, true))
				.isInstanceOfSatisfying(DomainException.class,
						exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN));
	}

	@Test
	void anotherUsersVersionIsHiddenAsNotFound() {
		BossRun run = createActiveRun(500);
		AuthenticatedUser owner = createUser("owner", UserStatus.ACTIVE);
		AuthenticatedUser attacker = createUser("attacker", UserStatus.ACTIVE);
		long ownersVersionId = createEvaluatedVersion(owner, 80).getId();

		assertThatThrownBy(() -> submissionService.submit(attacker, run.getId(), ownersVersionId, true))
				.isInstanceOfSatisfying(DomainException.class,
						exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.NOT_FOUND));
	}

	@Test
	void draftVersionIsRejectedByPolicy() {
		BossRun run = createActiveRun(500);
		AuthenticatedUser user = createUser("draft", UserStatus.ACTIVE);
		SentenceVersion draft = createDraftVersion(user);

		assertThatThrownBy(() -> submissionService.submit(user, run.getId(), draft.getId(), true))
				.isInstanceOfSatisfying(DomainException.class,
						exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.POLICY_REJECTED));
	}

	@Test
	void evaluatedVersionWithoutEvaluationRowIsRejectedByPolicy() {
		BossRun run = createActiveRun(500);
		AuthenticatedUser user = createUser("noeval", UserStatus.ACTIVE);
		Instant now = serviceClock.now();
		SentenceVersion version = createDraftVersion(user);
		version.startEvaluation(now);
		version.markEvaluated(now);
		version = versionRepository.save(version);
		long versionId = version.getId();

		assertThatThrownBy(() -> submissionService.submit(user, run.getId(), versionId, true))
				.isInstanceOfSatisfying(DomainException.class,
						exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.POLICY_REJECTED));
	}

	@Test
	void fourthSequentialSubmissionOnTheSameDayIsRateLimited() {
		BossRun run = createActiveRun(100_000);
		AuthenticatedUser user = createUser("limit", UserStatus.ACTIVE);
		for (int index = 0; index < 3; index++) {
			submissionService.submit(user, run.getId(), createEvaluatedVersion(user, 80).getId(), true);
		}
		long fourthVersionId = createEvaluatedVersion(user, 80).getId();

		assertThatThrownBy(() -> submissionService.submit(user, run.getId(), fourthVersionId, true))
				.isInstanceOfSatisfying(DomainException.class,
						exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.RATE_LIMITED));
		assertThat(submissionRepository.countByBossRunId(run.getId())).isEqualTo(3);
	}

	@Test
	void resubmittingTheSameVersionIsConflict() {
		BossRun run = createActiveRun(100_000);
		AuthenticatedUser user = createUser("resubmit", UserStatus.ACTIVE);
		long versionId = createEvaluatedVersion(user, 80).getId();
		submissionService.submit(user, run.getId(), versionId, true);

		assertThatThrownBy(() -> submissionService.submit(user, run.getId(), versionId, true))
				.isInstanceOfSatisfying(DomainException.class,
						exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.CONFLICT));
		assertThat(submissionRepository.countByBossRunId(run.getId())).isOne();
	}

	private BossRun createActiveRun(long hp) {
		Instant now = serviceClock.now();
		BossRun run = createScheduledRun(hp, now.minusSeconds(60));
		run.activate(now);
		phaseRepository.saveAll(run.getPhases());
		return runRepository.save(run);
	}

	/** 시작 시각이 아직 오지 않은 회차. 시각이 지난 예정 회차는 제출 경로의 지연 활성화가 진행 중으로 올린다. */
	private BossRun createScheduledRun(long hp) {
		return createScheduledRun(hp, serviceClock.now().plusSeconds(600));
	}

	private BossRun createScheduledRun(long hp, Instant startsAt) {
		Instant now = serviceClock.now();
		int suffix = SEQUENCE.incrementAndGet();
		Boss boss = bossRepository.save(Boss.create("Policy Boss " + suffix, "Test-only boss", now));
		BossRun run = BossRun.schedule(boss, startsAt, now.plusSeconds(3_600), now);
		run.addPhase(hp, BigDecimal.ZERO, null, BigDecimal.ONE);
		return runRepository.save(run);
	}

	private AuthenticatedUser createUser(String prefix, UserStatus status) {
		int suffix = SEQUENCE.incrementAndGet();
		User user = userRepository.save(
				User.createFixture(prefix + suffix + "@policy.test", "hash", prefix + suffix, status));
		return new AuthenticatedUser(user.getId(), user.getEmail());
	}

	private SentenceVersion createDraftVersion(AuthenticatedUser principal) {
		Instant now = serviceClock.now();
		User user = userRepository.findById(principal.userId()).orElseThrow();
		Sentence sentence = sentenceRepository.save(Sentence.create(user, now));
		return versionRepository.save(
				SentenceVersion.draft(sentence, 1, "정책 검증 문장 " + SEQUENCE.incrementAndGet(), now));
	}

	private SentenceVersion createEvaluatedVersion(AuthenticatedUser principal, int score) {
		Instant now = serviceClock.now();
		SentenceVersion version = createDraftVersion(principal);
		version.startEvaluation(now);
		version.markEvaluated(now);
		version = versionRepository.save(version);
		evaluationRepository.save(SentenceEvaluation.passed(version, score, score, score, score, score,
				BigDecimal.valueOf(score), BigDecimal.ZERO, BigDecimal.valueOf(score), "ok", "fake", "v1", "v1", now));
		return version;
	}
}
