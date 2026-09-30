package com.ssafy.c206.backend.boss.submission;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;

import com.ssafy.c206.backend.boss.domain.Boss;
import com.ssafy.c206.backend.boss.domain.BossPhase;
import com.ssafy.c206.backend.boss.domain.BossRun;
import com.ssafy.c206.backend.boss.repository.BossPhaseRepository;
import com.ssafy.c206.backend.boss.repository.BossRepository;
import com.ssafy.c206.backend.boss.repository.BossRunRepository;
import com.ssafy.c206.backend.boss.submission.service.BossSubmissionPreparation;
import com.ssafy.c206.backend.boss.submission.service.BossSubmissionResult;
import com.ssafy.c206.backend.boss.submission.service.BossSubmissionService;
import com.ssafy.c206.backend.boss.submission.service.BossSubmissionTransaction;
import com.ssafy.c206.backend.common.api.DomainException;
import com.ssafy.c206.backend.common.api.ErrorCode;
import com.ssafy.c206.backend.common.security.AuthenticatedUser;
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
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

/**
 * 데미지를 락 밖에서 계산하고 락 안에서 반영하는 구조가 만드는 시간 틈을 검증한다.
 *
 * <p>계산과 반영 사이에 다른 사용자가 페이즈를 넘겨 버릴 수 있다. 그 경우 계산해 둔 값은 이전
 * 페이즈의 하한과 배율을 쓴 것이므로 그대로 반영하면 안 된다. 이 테스트는 그 틈을 인위적으로
 * 만들기 위해 {@link BossSubmissionTransaction}의 두 경계를 직접 호출한다. 서비스 메서드로는
 * 두 단계가 붙어 실행되어 틈을 만들 수 없다.
 */
@SpringBootTest
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class BossSubmissionPhaseAdvanceTest {

	private static final AtomicInteger SEQUENCE = new AtomicInteger();

	@Autowired private BossSubmissionTransaction transaction;
	@Autowired private BossSubmissionService submissionService;
	@Autowired private BossRepository bossRepository;
	@Autowired private BossRunRepository runRepository;
	@Autowired private BossPhaseRepository phaseRepository;
	@Autowired private UserRepository userRepository;
	@Autowired private SentenceRepository sentenceRepository;
	@Autowired private SentenceVersionRepository versionRepository;
	@Autowired private SentenceEvaluationRepository evaluationRepository;
	@Autowired private ServiceClock serviceClock;

	@Test
	void damageIsRecalculatedAgainstTheNewPhaseWhenItAdvancesBeforeApply() {
		// 2페이즈의 배율이 1페이즈의 두 배다. 반영 시점의 페이즈 기준으로 계산되었는지를
		// 데미지 값 자체로 구분할 수 있게 만든다.
		BossRun run = createActiveRun(new BigDecimal("2.000"));
		AuthenticatedUser lateComer = createUser("late");
		AuthenticatedUser breaker = createUser("breaker");
		long lateVersionId = createEvaluatedVersion(lateComer, 90).getId();

		// 1페이즈 기준으로 검증·계산까지 끝낸 상태를 만든다.
		BossSubmissionPreparation preparation = transaction.prepare(lateComer.userId(), run.getId(), lateVersionId,
				true, serviceClock.now());
		assertThat(preparation.phaseNo()).isEqualTo(1);
		long damageAgainstFirstPhase = preparation.damage().finalDamage();

		// 그 사이 다른 사용자가 1페이즈를 깨서 회차를 2페이즈로 넘긴다.
		submissionService.submit(breaker, run.getId(), createEvaluatedVersion(breaker, 90).getId(), true);
		assertThat(currentPhaseNo(run)).isEqualTo(2);

		BossSubmissionResult result = transaction.apply(preparation, serviceClock.now());

		assertThat(result.bossPhaseNo()).isEqualTo(2);
		assertThat(result.phaseModifier()).isEqualByComparingTo(new BigDecimal("2.000"));
		assertThat(result.finalDamage()).isGreaterThan(damageAgainstFirstPhase);
	}

	@Test
	void submissionIsRejectedWhenTheNewPhaseRaisesTheHardMinimumAboveTheScore() {
		// 2페이즈의 하한이 제출 점수보다 높다. 페이즈가 올라가면 요구 점수도 올라가므로
		// 이전 페이즈 기준으로 통과한 문장이 거절되는 것이 정책이다.
		BossRun run = createActiveRun(BigDecimal.ONE, new BigDecimal("95.00"));
		AuthenticatedUser lateComer = createUser("belowmin");
		AuthenticatedUser breaker = createUser("breaker2");
		long lateVersionId = createEvaluatedVersion(lateComer, 90).getId();

		BossSubmissionPreparation preparation = transaction.prepare(lateComer.userId(), run.getId(), lateVersionId,
				true, serviceClock.now());
		submissionService.submit(breaker, run.getId(), createEvaluatedVersion(breaker, 90).getId(), true);
		assertThat(currentPhaseNo(run)).isEqualTo(2);

		assertThatThrownBy(() -> transaction.apply(preparation, serviceClock.now()))
				.isInstanceOfSatisfying(DomainException.class,
						exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.POLICY_REJECTED));
	}

	@Test
	void preparedSubmissionIsRejectedWhenTheRunEndsBeforeApply() {
		// 반영 단계가 회차 상태를 다시 확인하지 않으면, 종료된 회차에 데미지가 들어간다.
		BossRun run = createActiveRun(BigDecimal.ONE);
		AuthenticatedUser user = createUser("ended");
		long versionId = createEvaluatedVersion(user, 90).getId();
		BossSubmissionPreparation preparation = transaction.prepare(user.userId(), run.getId(), versionId, true,
				serviceClock.now());

		endRun(run);

		assertThatThrownBy(() -> transaction.apply(preparation, serviceClock.now()))
				.isInstanceOfSatisfying(DomainException.class,
						exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.CONFLICT));
	}

	private BossRun createActiveRun(BigDecimal secondPhaseMultiplier) {
		return createActiveRun(secondPhaseMultiplier, BigDecimal.ZERO);
	}

	/** 1페이즈는 한 번의 제출로 깨지는 HP를 갖고, 2페이즈는 인자로 받은 값을 쓴다. */
	private BossRun createActiveRun(BigDecimal secondPhaseMultiplier, BigDecimal secondPhaseHardMin) {
		Instant now = serviceClock.now();
		int suffix = SEQUENCE.incrementAndGet();
		Boss boss = bossRepository.save(Boss.create("Advance Boss " + suffix, "Test-only boss", now));
		BossRun run = BossRun.schedule(boss, now.minusSeconds(60), now.plusSeconds(3_600), now);
		run.addPhase(1, BigDecimal.ZERO, null, BigDecimal.ONE);
		run.addPhase(100_000, secondPhaseHardMin, null, secondPhaseMultiplier);
		run = runRepository.save(run);
		run.activate(now);
		phaseRepository.saveAll(run.getPhases());
		return runRepository.save(run);
	}

	private void endRun(BossRun run) {
		Instant now = serviceClock.now();
		BossRun loaded = runRepository.findById(run.getId()).orElseThrow();
		loaded.markEndedUncleared(now);
		runRepository.save(loaded);
	}

	private int currentPhaseNo(BossRun run) {
		return runRepository.findById(run.getId()).orElseThrow().getCurrentPhaseNo();
	}

	private AuthenticatedUser createUser(String prefix) {
		int suffix = SEQUENCE.incrementAndGet();
		User user = userRepository.save(
				User.createFixture(prefix + suffix + "@advance.test", "hash", prefix + suffix, UserStatus.ACTIVE));
		return new AuthenticatedUser(user.getId(), user.getEmail());
	}

	private SentenceVersion createEvaluatedVersion(AuthenticatedUser principal, int score) {
		Instant now = serviceClock.now();
		User user = userRepository.findById(principal.userId()).orElseThrow();
		Sentence sentence = sentenceRepository.save(Sentence.create(user, now));
		SentenceVersion version = versionRepository.save(
				SentenceVersion.draft(sentence, 1, "페이즈 전환 검증 문장 " + SEQUENCE.incrementAndGet(), now));
		version.startEvaluation(now);
		version.markEvaluated(now);
		version = versionRepository.save(version);
		evaluationRepository.save(SentenceEvaluation.passed(version, score, score, score, score, score,
				BigDecimal.valueOf(score), BigDecimal.ZERO, BigDecimal.valueOf(score), "ok", "fake", "v1", "v1", now));
		return version;
	}
}
