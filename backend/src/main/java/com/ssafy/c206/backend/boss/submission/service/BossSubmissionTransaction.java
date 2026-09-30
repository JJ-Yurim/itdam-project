package com.ssafy.c206.backend.boss.submission.service;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import com.ssafy.c206.backend.boss.domain.BossMood;
import com.ssafy.c206.backend.boss.domain.BossPhase;
import com.ssafy.c206.backend.boss.domain.BossRun;
import com.ssafy.c206.backend.boss.domain.BossRunStatus;
import com.ssafy.c206.backend.boss.domain.MoodRole;
import com.ssafy.c206.backend.boss.repository.BossMoodRepository;
import com.ssafy.c206.backend.boss.repository.BossPhaseRepository;
import com.ssafy.c206.backend.boss.repository.BossRunRepository;
import com.ssafy.c206.backend.boss.submission.domain.BossSubmission;
import com.ssafy.c206.backend.boss.submission.domain.BossSubmissionDailyCounter;
import com.ssafy.c206.backend.boss.submission.repository.BossSubmissionDailyCounterRepository;
import com.ssafy.c206.backend.boss.submission.repository.BossSubmissionRepository;
import com.ssafy.c206.backend.common.api.DomainException;
import com.ssafy.c206.backend.common.api.ErrorCode;
import com.ssafy.c206.backend.common.time.ServiceClock;
import com.ssafy.c206.backend.integration.similarity.SentenceSimilarityService;
import com.ssafy.c206.backend.sentence.domain.SentenceVersion;
import com.ssafy.c206.backend.sentence.evaluation.service.EvaluatedSentenceQueryService;
import com.ssafy.c206.backend.user.domain.User;
import com.ssafy.c206.backend.user.service.UserQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 제출 유스케이스의 트랜잭션 경계.
 *
 * <p>하나의 제출을 두 경계로 나눈다. {@link #prepare}는 잠금 없이 검증하고 데미지를 계산하며,
 * {@link #apply}는 짧은 쓰기 트랜잭션에서 HP만 반영한다.
 *
 * <p>나눈 이유는 락 보유 시간이다. 이전에는 하나의 트랜잭션이 회차 행을 잠근 뒤 커밋까지
 * 사용자·문장·평가 조회, 중복 확인, pgvector 유사도 검사, 무드 조회, 데미지 계산을 모두 수행했다.
 * 회차 행은 모든 참여자가 공유하므로, 같은 회차에 동시에 제출하는 사용자 전원이 그 작업 전체를
 * 직렬로 기다렸다. 실측에서 동시 사용자 N에 대해 항상 N-1개 세션이 락을 대기했다
 * (docs/operations/boss-submission-load-test.md).
 *
 * <p>락이 실제로 필요한 것은 페이즈 HP 갱신, 누적 데미지 갱신, 페이즈 전환뿐이다.
 */
@Service
@RequiredArgsConstructor
public class BossSubmissionTransaction {

	private final BossRunRepository bossRunRepository;
	private final BossPhaseRepository bossPhaseRepository;
	private final BossMoodRepository bossMoodRepository;
	private final BossSubmissionDailyCounterRepository counterRepository;
	private final BossSubmissionRepository submissionRepository;
	private final UserQueryService userQueryService;
	private final EvaluatedSentenceQueryService evaluatedSentences;
	private final SentenceSimilarityService similarityService;
	private final DamageCalculator damageCalculator;
	private final BossSubmissionPolicy submissionPolicy;
	private final ServiceClock serviceClock;
	private final ApplicationEventPublisher eventPublisher;

	/**
	 * 잠금 없이 제출 적격성을 검증하고 데미지를 계산한다.
	 *
	 * <p>여기서 통과했다고 반영이 보장되지는 않는다. 회차 상태와 페이즈는 {@link #apply}가 락 안에서
	 * 다시 확인하며, 그것이 권위 있는 판정이다. 이 단계의 검증은 실패할 요청을 락에 도달하기 전에
	 * 걸러내기 위한 것이다.
	 */
	@Transactional(readOnly = true)
	public BossSubmissionPreparation prepare(long userId, long bossRunId, long sentenceVersionId,
			boolean consentAccepted, Instant requestTime) {
		if (!consentAccepted) {
			throw new DomainException(ErrorCode.POLICY_REJECTED, "Submission consent must be accepted.");
		}
		BossRun run = bossRunRepository.findWithBossById(bossRunId)
				.orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND));
		ensureActiveAndNotExpired(run, requestTime);
		BossPhase phase = bossPhaseRepository.findCurrentByBossRunId(run.getId(), run.getCurrentPhaseNo())
				.orElseThrow(() -> new DomainException(ErrorCode.CONFLICT, "Boss run has no current phase."));
		// 활성 사용자 검증은 user 도메인의 경계 서비스가 담당한다 (비활성 → FORBIDDEN).
		userQueryService.getActiveUser(userId);
		SentenceVersion version = evaluatedSentences.getVersionWithSentence(sentenceVersionId);
		var evaluation = evaluatedSentences.getEligibleEvaluation(userId, version);
		// 사전 확인이다. 확인과 삽입 사이에 끼어드는 동시 요청은 apply의 유니크 제약이 막는다.
		if (submissionRepository.existsByBossRunIdAndSentenceVersionId(run.getId(), version.getId())) {
			throw new DomainException(ErrorCode.CONFLICT, "This sentence version was already submitted to the boss run.");
		}
		// 한도를 넘긴 요청이 유사도 검사와 회차 잠금까지 지나간 뒤에 거절되면, 거절될 요청이 다른
		// 사용자의 제출을 막아 세운다. 권위 있는 판정은 apply의 잠금 안에 있고 이것은 조기 거절이다.
		ensureDailyLimitNotAlreadyReached(run.getId(), userId, serviceClock.dateAt(requestTime));
		// 수용한 완화: 유사도 검사가 잠금 밖으로 나오면서, 같은 사용자가 유사한 두 문장을 동시에
		// (더블클릭·재시도) 제출하면 서로를 보지 못해 둘 다 통과할 수 있다. 이전에는 회차 잠금이
		// 모든 제출을 직렬화해 불가능했다. 완전히 막으려면 두 트랜잭션에 걸치는 사용자 단위
		// 직렬화가 필요한데, 하루 3회 한도에서 이 경합의 실익이 없어 감수한다. 순차 재제출은
		// 그때의 prepare가 앞선 제출을 보므로 여전히 걸린다.
		if (similarityService.isSimilar(userId, run.getId(), version.getId())) {
			throw new DomainException(ErrorCode.POLICY_REJECTED, "The sentence is too similar to a prior submission.");
		}
		DamageCalculationCommand command = new DamageCalculationCommand(
				evaluation.getBaseScore(), evaluation.getWordBonus(), evaluation.getGameBaseAttack(),
				phase.getHardMinScore(), phase.getRecommendedScore(), phase.getDamageMultiplier(),
				sentenceMoodSnapshots(version), bossMoodSnapshots(run));
		return new BossSubmissionPreparation(userId, run.getId(), version.getId(), phase.getPhaseNo(),
				serviceClock.dateAt(requestTime), command, damageCalculator.calculate(command));
	}

	/**
	 * 계산된 데미지를 반영한다. 회차와 현재 페이즈를 잠그는 유일한 구간이다.
	 *
	 * <p>이 구간에는 조회·계산을 두지 않는다. 사용자와 문장 버전은 FK를 채우기 위한 지연 참조로만
	 * 가져오므로 SELECT가 발생하지 않는다.
	 */
	@Transactional
	public BossSubmissionResult apply(BossSubmissionPreparation preparation, Instant requestTime) {
		// The run lock serializes competing submissions before the current phase write lock.
		BossRun run = bossRunRepository.findWithBossByIdForUpdate(preparation.bossRunId())
				.orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND));
		ensureActiveAndNotExpired(run, requestTime);
		BossPhase phase = bossPhaseRepository.findCurrentByBossRunIdForUpdate(run.getId(), run.getCurrentPhaseNo())
				.orElseThrow(() -> new DomainException(ErrorCode.CONFLICT, "Boss run has no current phase."));
		DamageCalculation damage = damageForCurrentPhase(preparation, phase);
		User user = userQueryService.getReference(preparation.userId());
		BossSubmissionDailyCounter counter = counterRepository
				.findByRunUserDateForUpdate(run.getId(), preparation.userId(), preparation.serviceDate())
				.orElseGet(() -> counterRepository.save(
						BossSubmissionDailyCounter.start(run, user, preparation.serviceDate(), requestTime)));
		counter.incrementWithin(submissionPolicy.getDailySubmissionLimit(), requestTime);
		BossSubmission submission = saveSubmission(run, phase, user, preparation, damage, requestTime);
		boolean cleared = phase.applyDamage(damage.finalDamage());
		run.addAccumulatedDamage(damage.finalDamage(), requestTime);
		if (cleared) {
			BossPhase next = bossPhaseRepository.findCurrentByBossRunIdForUpdate(run.getId(), phase.getPhaseNo() + 1)
					.orElse(null);
			run.advanceAfterCurrentPhaseClear(phase, requestTime, next);
		}
		// 트랜잭션 안에서 발행하고 전파는 AFTER_COMMIT 리스너가 맡는다. 롤백되면 전파되지 않는다.
		eventPublisher.publishEvent(new BossSubmissionAppliedEvent(run.getId(), preparation.userId(),
				phase.getPhaseNo(), phase.getRemainingHp(), phase.getMaxHp(), run.getAccumulatedDamage(),
				damage.finalDamage(), damage.baseScore(), run.getStatus()));
		return BossSubmissionResult.from(submission, phase, run, damage, preparation.serviceDate());
	}

	/**
	 * 계산 시점과 반영 시점의 페이즈가 같으면 계산해 둔 값을 쓰고, 그 사이 페이즈가 넘어갔으면
	 * 새 페이즈의 하한·배율로 한 번 다시 계산한다.
	 *
	 * <p>재계산 결과가 새 페이즈의 하한에 미달해 POLICY_REJECTED가 나는 것은 올바른 동작이다.
	 * 페이즈가 올라가면 요구 점수도 올라가므로, 이전 페이즈 기준으로 통과한 문장이 새 페이즈에서
	 * 거절되는 것이 정책이다.
	 */
	private DamageCalculation damageForCurrentPhase(BossSubmissionPreparation preparation, BossPhase phase) {
		if (phase.getPhaseNo() == preparation.phaseNo()) {
			return preparation.damage();
		}
		return damageCalculator.calculate(preparation.damageCommand()
				.withPhase(phase.getHardMinScore(), phase.getRecommendedScore(), phase.getDamageMultiplier()));
	}

	/**
	 * 같은 회차에 같은 문장 버전이 두 번 들어가는 것은 {@code uk_boss_submission_run_version}이 막는다.
	 * flush를 명시해 커밋 시점이 아니라 이 자리에서 위반을 잡고 CONFLICT로 바꾼다. 그렇지 않으면
	 * 제약 위반이 커밋 단계에서 터져 INTERNAL_ERROR로 나간다.
	 *
	 * <p>이 flush에는 앞선 카운터 INSERT도 함께 실려 나가지만, 카운터의 유일 제약이 여기서 터질 일은
	 * 없다. 회차 행 잠금이 같은 회차의 제출을 직렬화하므로 같은 (회차, 사용자, 날짜) 카운터를 두
	 * 요청이 동시에 만들 수 없다. 즉 이 자리에서 잡히는 위반은 문장 버전 중복이다.
	 */
	private BossSubmission saveSubmission(BossRun run, BossPhase phase, User user,
			BossSubmissionPreparation preparation, DamageCalculation damage, Instant requestTime) {
		SentenceVersion version = evaluatedSentences.getVersionReference(preparation.sentenceVersionId());
		try {
			return submissionRepository.saveAndFlush(BossSubmission.create(run, phase, user, version,
					preparation.serviceDate(), damage, requestTime));
		} catch (DataIntegrityViolationException exception) {
			// 이 flush에서는 다른 위반도 가능하다. 지연 참조로 채운 FK의 대상 행이 prepare와 apply
			// 사이에 사라진 경우가 그렇다. 제약 이름을 확인하지 않고 전부 CONFLICT로 바꾸면 그런
			// 경우까지 "이미 제출했다"로 오진하므로, 중복 제약 위반만 변환하고 나머지는 그대로 던진다.
			if (isDuplicateSubmissionViolation(exception)) {
				throw new DomainException(ErrorCode.CONFLICT,
						"This sentence version was already submitted to the boss run.");
			}
			throw exception;
		}
	}

	private static boolean isDuplicateSubmissionViolation(DataIntegrityViolationException exception) {
		String message = String.valueOf(exception.getMostSpecificCause().getMessage());
		// H2는 제약 이름을 대문자로 보고하므로 대소문자를 무시하고 비교한다.
		return message.toLowerCase().contains("uk_boss_submission_run_version");
	}

	/**
	 * 잠금 없이 읽은 카운터로 이미 한도에 도달한 요청을 미리 거절한다.
	 *
	 * <p>여기서 통과해도 한도를 넘기지 않는다는 보장은 없다. 같은 사용자의 동시 요청은 이 시점의
	 * 값을 똑같이 읽을 수 있기 때문이다. 그 경합은 apply가 카운터 행을 잠그고 판정한다.
	 */
	private void ensureDailyLimitNotAlreadyReached(long bossRunId, long userId, LocalDate serviceDate) {
		counterRepository.findByBossRunIdAndUserIdAndServiceDate(bossRunId, userId, serviceDate)
				.filter(counter -> counter.getSubmissionCount() >= submissionPolicy.getDailySubmissionLimit())
				.ifPresent(counter -> {
					throw new DomainException(ErrorCode.RATE_LIMITED, "Daily boss submission limit has been reached.");
				});
	}

	private void ensureActiveAndNotExpired(BossRun run, Instant now) {
		if (run.getStatus() != BossRunStatus.ACTIVE) {
			throw new DomainException(ErrorCode.CONFLICT, "Boss run is not active.");
		}
		if (run.getEndsAt() != null && !now.isBefore(run.getEndsAt())) {
			throw new DomainException(ErrorCode.CONFLICT, "Boss run has ended.");
		}
	}

	private List<SentenceMoodSnapshot> sentenceMoodSnapshots(SentenceVersion version) {
		return evaluatedSentences.getMoodsRanked(version.getId()).stream()
				.map(mood -> new SentenceMoodSnapshot(mood.getMoodCategory().getCode(), mood.getStrength())).toList();
	}

	private List<BossMoodSnapshot> bossMoodSnapshots(BossRun run) {
		return bossMoodRepository.findDetailsByBossIdIn(List.of(run.getBoss().getId())).stream()
				.map(mood -> new BossMoodSnapshot(mood.getMoodCategory().getCode(), role(mood), mood.getWeight())).toList();
	}

	private SubmissionMoodRole role(BossMood mood) {
		return mood.getMoodRole() == MoodRole.THEME ? SubmissionMoodRole.THEME : SubmissionMoodRole.WEAKNESS;
	}
}
