package com.ssafy.c206.backend.boss.service;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import com.ssafy.c206.backend.boss.api.EligibleSentenceResponse;
import com.ssafy.c206.backend.boss.domain.BossMood;
import com.ssafy.c206.backend.boss.domain.BossPhase;
import com.ssafy.c206.backend.boss.domain.BossRunStatus;
import com.ssafy.c206.backend.boss.domain.MoodRole;
import com.ssafy.c206.backend.boss.repository.BossMoodRepository;
import com.ssafy.c206.backend.boss.repository.BossPhaseRepository;
import com.ssafy.c206.backend.boss.repository.BossRunRepository;
import com.ssafy.c206.backend.boss.submission.repository.BossSubmissionRepository;
import com.ssafy.c206.backend.boss.submission.service.BossMoodSnapshot;
import com.ssafy.c206.backend.boss.submission.service.DamageCalculationCommand;
import com.ssafy.c206.backend.boss.submission.service.DamageCalculator;
import com.ssafy.c206.backend.boss.submission.service.SentenceMoodSnapshot;
import com.ssafy.c206.backend.boss.submission.service.SubmissionMoodRole;
import com.ssafy.c206.backend.common.api.DomainException;
import com.ssafy.c206.backend.common.api.ErrorCode;
import com.ssafy.c206.backend.common.api.PageResponse;
import com.ssafy.c206.backend.sentence.evaluation.domain.SentenceEvaluation;
import com.ssafy.c206.backend.sentence.evaluation.domain.SentenceMood;
import com.ssafy.c206.backend.sentence.evaluation.service.EligibleSentenceRevision;
import com.ssafy.c206.backend.sentence.evaluation.service.EvaluatedSentenceQueryService;
import com.ssafy.c206.backend.common.security.AuthenticatedUser;
import com.ssafy.c206.backend.common.security.AuthenticatedUsers;
import com.ssafy.c206.backend.common.time.ServiceClock;
import com.ssafy.c206.backend.user.service.UserQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class BossEligibleSentenceQueryService {
	private static final int MAX_CACHED_STATES = 128;

	private final BossRunLifecycleService lifecycleService;
	private final BossRunRepository bossRunRepository;
	private final BossPhaseRepository bossPhaseRepository;
	private final BossMoodRepository bossMoodRepository;
	private final BossSubmissionRepository submissionRepository;
	private final EvaluatedSentenceQueryService evaluatedSentenceQueryService;
	private final DamageCalculator damageCalculator;
	private final UserQueryService userQueryService;
	private final ServiceClock serviceClock;
	/** 페이지 이동은 같은 정렬 결과를 잘라 쓰므로 후보 상태별 계산 결과를 제한적으로 재사용한다. */
	private final Map<EligibleCacheKey, List<EligibleSentenceResponse>> sortedResponseCache = new ConcurrentHashMap<>();

	@Transactional(readOnly = true)
	public PageResponse<EligibleSentenceResponse> getEligible(AuthenticatedUser principal, Long bossRunId,
			int page, int size) {
		long userId = AuthenticatedUsers.requireUserId(principal);
		userQueryService.getActiveUser(userId);
		lifecycleService.syncStateIfNeeded(bossRunId, serviceClock.now());
		var run = bossRunRepository.findWithBossById(bossRunId)
				.orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND));
		if (run.getStatus() != BossRunStatus.ACTIVE) {
			throw new DomainException(ErrorCode.CONFLICT, "Boss run is not active.");
		}
		BossPhase phase = bossPhaseRepository.findCurrentByBossRunId(run.getId(), run.getCurrentPhaseNo())
				.orElseThrow(() -> new DomainException(ErrorCode.CONFLICT, "Boss run has no current phase."));
		// 이 런에 이미 제출된 버전(보스 도메인 지식)을 제외 목록으로 넘겨 sentence 쿼리의 역방향 의존을 없앤다.
		List<Long> submittedVersionIds = submissionRepository.findSubmittedSentenceVersionIds(bossRunId, userId)
				.stream().sorted().toList();
		List<BossMoodSnapshot> bossMoods = bossMoodRepository.findDetailsByBossIdIn(List.of(run.getBoss().getId()))
				.stream().map(this::bossMoodSnapshot).toList();
		BigDecimal hardMinScore = phase.getHardMinScore() == null ? BigDecimal.ZERO : phase.getHardMinScore();
		EligibleSentenceRevision revision = evaluatedSentenceQueryService.getEligibleRevision(userId);
		EligibleCacheKey cacheKey = new EligibleCacheKey(run.getId(), userId, phase.getPhaseNo(), hardMinScore,
				phase.getRecommendedScore(), phase.getDamageMultiplier(), submittedVersionIds, bossMoods, revision);
		List<EligibleSentenceResponse> sorted = cachedOrCalculate(cacheKey, userId, submittedVersionIds, phase,
				bossMoods);
		return page(sorted, page, size);
	}

	private List<EligibleSentenceResponse> cachedOrCalculate(EligibleCacheKey cacheKey, long userId,
			List<Long> submittedVersionIds, BossPhase phase, List<BossMoodSnapshot> bossMoods) {
		if (sortedResponseCache.size() >= MAX_CACHED_STATES && !sortedResponseCache.containsKey(cacheKey)) {
			sortedResponseCache.clear();
		}
		return sortedResponseCache.computeIfAbsent(cacheKey, ignored -> calculateSorted(userId,
				submittedVersionIds, phase, bossMoods));
	}

	private List<EligibleSentenceResponse> calculateSorted(long userId, List<Long> submittedVersionIds,
			BossPhase phase, List<BossMoodSnapshot> bossMoods) {
		List<SentenceEvaluation> evaluations = evaluatedSentenceQueryService
				.getAllEligibleForSubmission(userId, submittedVersionIds);
		if (evaluations.isEmpty()) {
			return List.of();
		}
		List<Long> versionIds = evaluations.stream()
				.map(evaluation -> evaluation.getSentenceVersion().getId()).toList();
		Map<Long, List<SentenceMood>> moodsByVersion = evaluatedSentenceQueryService.getMoodsForVersions(versionIds)
				.stream().collect(Collectors.groupingBy(mood -> mood.getSentenceVersion().getId()));
		return evaluations.stream()
				.map(evaluation -> response(evaluation, phase,
						moodsByVersion.getOrDefault(evaluation.getSentenceVersion().getId(), List.of()), bossMoods))
				.sorted(Comparator.comparingLong(EligibleSentenceResponse::sentenceEnergy).reversed()
						.thenComparing(EligibleSentenceResponse::sentenceVersionId, Comparator.reverseOrder()))
				.toList();
	}

	private EligibleSentenceResponse response(SentenceEvaluation evaluation, BossPhase phase,
			List<SentenceMood> sentenceMoods, List<BossMoodSnapshot> bossMoods) {
		// 최소 점수 미달 문장은 기존 계약대로 목록에 남기되 실제 제출이 거절되므로 에너지는 0으로 표시한다.
		BigDecimal hardMinScore = phase.getHardMinScore() == null ? BigDecimal.ZERO : phase.getHardMinScore();
		long sentenceEnergy = 0;
		if (evaluation.getBaseScore().compareTo(hardMinScore) >= 0) {
			DamageCalculationCommand command = new DamageCalculationCommand(
					evaluation.getBaseScore(), evaluation.getWordBonus(), evaluation.getGameBaseAttack(),
					hardMinScore, phase.getRecommendedScore(), phase.getDamageMultiplier(),
					sentenceMoods.stream().map(this::sentenceMoodSnapshot).toList(), bossMoods);
			sentenceEnergy = damageCalculator.calculate(command).finalDamage();
		}
		return EligibleSentenceResponse.from(evaluation, sentenceEnergy);
	}

	private SentenceMoodSnapshot sentenceMoodSnapshot(SentenceMood mood) {
		return new SentenceMoodSnapshot(mood.getMoodCategory().getCode(), mood.getStrength());
	}

	private BossMoodSnapshot bossMoodSnapshot(BossMood mood) {
		SubmissionMoodRole role = mood.getMoodRole() == MoodRole.THEME
				? SubmissionMoodRole.THEME : SubmissionMoodRole.WEAKNESS;
		return new BossMoodSnapshot(mood.getMoodCategory().getCode(), role, mood.getWeight());
	}

	private PageResponse<EligibleSentenceResponse> page(List<EligibleSentenceResponse> sorted, int page, int size) {
		long totalElements = sorted.size();
		int totalPages = (int) ((totalElements + size - 1) / size);
		long requestedStart = (long) page * size;
		int start = (int) Math.min(requestedStart, totalElements);
		int end = (int) Math.min(requestedStart + size, totalElements);
		return new PageResponse<>(sorted.subList(start, end), page, size, totalElements, totalPages,
				page + 1 < totalPages);
	}

	private record EligibleCacheKey(long bossRunId, long userId, int phaseNo, BigDecimal hardMinScore,
			BigDecimal recommendedScore, BigDecimal damageMultiplier, List<Long> submittedVersionIds,
			List<BossMoodSnapshot> bossMoods, EligibleSentenceRevision revision) {
		private EligibleCacheKey {
			submittedVersionIds = List.copyOf(submittedVersionIds);
			bossMoods = List.copyOf(bossMoods);
		}
	}
}
