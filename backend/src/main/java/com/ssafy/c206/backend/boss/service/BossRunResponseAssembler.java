package com.ssafy.c206.backend.boss.service;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.ssafy.c206.backend.boss.api.BossMoodResponse;
import com.ssafy.c206.backend.boss.api.BossPhaseResponse;
import com.ssafy.c206.backend.boss.api.BossRunResponse;
import com.ssafy.c206.backend.boss.api.BossRunSummaryResponse;
import com.ssafy.c206.backend.boss.api.BossSubmissionSummary;
import com.ssafy.c206.backend.boss.domain.BossMood;
import com.ssafy.c206.backend.boss.domain.BossPhase;
import com.ssafy.c206.backend.boss.domain.BossRun;
import com.ssafy.c206.backend.boss.image.service.BossImageUrlResolver;
import com.ssafy.c206.backend.boss.repository.BossMoodRepository;
import com.ssafy.c206.backend.boss.repository.BossPhaseRepository;
import com.ssafy.c206.backend.boss.submission.repository.BossSubmissionDailyCounterRepository;
import com.ssafy.c206.backend.boss.submission.repository.BossSubmissionRepository;
import com.ssafy.c206.backend.boss.submission.service.BossSubmissionPolicy;
import com.ssafy.c206.backend.common.api.DomainException;
import com.ssafy.c206.backend.common.api.ErrorCode;
import com.ssafy.c206.backend.common.time.ServiceClock;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 보스 런 목록·상세 응답을 벌크 조회로 조립한다.
 * 페이즈·무드·오늘 제출 수·내 제출 요약을 런 수와 무관하게 각 1회 쿼리로 적재한다.
 */
@Component
@RequiredArgsConstructor
public class BossRunResponseAssembler {

	private final BossPhaseRepository bossPhaseRepository;
	private final BossMoodRepository bossMoodRepository;
	private final BossSubmissionDailyCounterRepository counterRepository;
	private final BossSubmissionRepository submissionRepository;
	private final BossImageUrlResolver imageUrlResolver;
	private final BossSubmissionPolicy submissionPolicy;
	private final ServiceClock serviceClock;

	public List<BossRunResponse> assemble(List<BossRun> runs, Long userId) {
		if (runs.isEmpty()) return List.of();
		Shared shared = loadShared(runs, userId);
		Map<Long, List<BossSubmissionSummary>> submissions = new HashMap<>();
		if (userId != null) {
			submissionRepository.findDetailsByBossRunIdInAndUserId(shared.runIds(), userId).stream()
					.collect(Collectors.groupingBy(submission -> submission.getBossRun().getId()))
					.forEach((runId, values) -> submissions.put(runId, values.stream().map(BossSubmissionSummary::from).toList()));
		}
		return runs.stream().map(run -> BossRunResponse.from(run, currentPhaseResponse(run, shared),
				moodResponses(run, shared), shared.submittedToday(run),
				remainingSubmissionCount(userId, shared.submittedToday(run)),
				submissions.getOrDefault(run.getId(), List.of()))).toList();
	}

	/**
	 * 목록용 조립. 상세와 달리 제출 이력을 읽지 않는다.
	 *
	 * <p>제출 이력은 회차 수만큼이 아니라 그 사용자의 제출 건수만큼 늘어나고 문장 원문까지
	 * 딸려 온다. 목록은 카드에 그 값을 쓰지 않으므로, 한 페이지를 여는 대가로 모든 회차의
	 * 제출 전문을 읽어 오지 않도록 쿼리 자체를 생략한다.
	 */
	public List<BossRunSummaryResponse> assembleSummaries(List<BossRun> runs, Long userId) {
		if (runs.isEmpty()) return List.of();
		Shared shared = loadShared(runs, userId);
		return runs.stream().map(run -> BossRunSummaryResponse.from(run, currentPhaseResponse(run, shared),
				moodResponses(run, shared), shared.submittedToday(run),
				remainingSubmissionCount(userId, shared.submittedToday(run)))).toList();
	}

	/** 목록과 상세가 함께 쓰는 적재분. 회차 수와 무관하게 각 1회 쿼리로 읽는다. */
	private record Shared(List<Long> runIds, Map<Long, BossPhase> phases, Map<Long, List<BossMood>> moods,
			Map<Long, Integer> counts) {

		int submittedToday(BossRun run) {
			return counts.getOrDefault(run.getId(), 0);
		}
	}

	private Shared loadShared(List<BossRun> runs, Long userId) {
		List<Long> runIds = runs.stream().map(BossRun::getId).toList();
		Map<Long, BossPhase> phases = bossPhaseRepository.findDetailsByBossRunIdIn(runIds).stream()
				.filter(phase -> phase.getPhaseNo() == phase.getBossRun().getCurrentPhaseNo())
				.collect(Collectors.toMap(phase -> phase.getBossRun().getId(), Function.identity()));
		Map<Long, List<BossMood>> moods = bossMoodRepository.findDetailsByBossIdIn(
				runs.stream().map(run -> run.getBoss().getId()).toList()).stream()
				.collect(Collectors.groupingBy(mood -> mood.getBoss().getId()));
		Map<Long, Integer> counts = new HashMap<>();
		if (userId != null) {
			counterRepository.findByBossRunIdInAndUserIdAndServiceDate(runIds, userId, serviceClock.today())
					.forEach(counter -> counts.put(counter.getBossRun().getId(), counter.getSubmissionCount()));
		}
		return new Shared(runIds, phases, moods, counts);
	}

	private BossPhaseResponse currentPhaseResponse(BossRun run, Shared shared) {
		return BossPhaseResponse.from(requireCurrentPhase(run, shared.phases()), imageUrlResolver);
	}

	private static List<BossMoodResponse> moodResponses(BossRun run, Shared shared) {
		return shared.moods().getOrDefault(run.getBoss().getId(), List.of()).stream()
				.sorted(Comparator.comparing(BossMood::getMoodRole).thenComparing(BossMood::getId))
				.map(BossMoodResponse::from).toList();
	}

	/**
	 * 익명 열람에서는 잔여 제출 횟수를 0으로 내린다. mySubmissionCount와 달리 이 값은
	 * "제출할 수 있다"는 행동 가능성 진술이라, 제출 권한이 없는 익명에게 한도를 그대로
	 * 내리면 클라이언트가 이 값만 보고 제출 UI를 활성화해 401로 끝나는 흐름이 생긴다.
	 */
	private int remainingSubmissionCount(Long userId, int submittedToday) {
		if (userId == null) {
			return 0;
		}
		return submissionPolicy.remainingSubmissions(submittedToday);
	}

	private BossPhase requireCurrentPhase(BossRun run, Map<Long, BossPhase> phases) {
		BossPhase phase = phases.get(run.getId());
		if (phase == null) {
			throw new DomainException(ErrorCode.CONFLICT, "Boss run has no current phase.");
		}
		return phase;
	}
}
