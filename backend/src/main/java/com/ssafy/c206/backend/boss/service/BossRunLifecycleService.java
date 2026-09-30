package com.ssafy.c206.backend.boss.service;

import com.ssafy.c206.backend.boss.domain.BossRun;
import com.ssafy.c206.backend.boss.domain.BossRunStatus;
import com.ssafy.c206.backend.boss.repository.BossRepository;
import com.ssafy.c206.backend.boss.repository.BossRunRepository;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Performs the intentionally scheduler-free, short lazy lifecycle transitions.
 *
 * <p>시각이 지났다고 상태를 바꾸는 것이 아니라, 그 회차를 읽으려는 요청이 응답을 만들기 직전에
 * 밀린 전이를 처리한다. 만료(ACTIVE → ENDED_UNCLEARED)와 활성화(SCHEDULED → ACTIVE)를 함께
 * 다루며 순서가 중요하다. 끝난 회차를 먼저 만료시켜야 보스당 ACTIVE 유일 슬롯이 비고, 같은
 * 보스의 다음 예정 회차가 활성화될 수 있다.
 *
 * <p>시각은 호출부가 읽어서 넘긴다. 한 요청 안에서 가드와 전이가 서로 다른 시각을 보면, 가드는
 * 참이었는데 전이 대상은 없는(또는 그 반대) 어긋남이 생긴다.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class BossRunLifecycleService {

	/**
	 * 한 요청이 처리할 활성화 건수 상한. 조회 응답에 얹혀 도는 작업이라 후보가 많으면 그 지연이
	 * 그대로 사용자에게 간다. 넘친 회차는 다음 조회에서 이어서 처리된다.
	 */
	private static final int ACTIVATION_BATCH_LIMIT = 20;

	private final BossRunRepository bossRunRepository;
	private final BossRepository bossRepository;

	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void expireIfNeeded(Long bossRunId, Instant requestTime) {
		bossRunRepository.expireIfActiveAndEnded(bossRunId, requestTime);
	}

	/**
	 * 회차 하나를 읽기 직전에 만료·활성화 양방향으로 맞춘다.
	 *
	 * <p>목록 경로와 달리 요청된 회차만 본다. 같은 보스에 시작 시각이 더 이른 예정 회차가 있어도
	 * 여기서는 요청된 회차가 먼저 진행된다. 사용자가 연 회차를 두고 다른 회차를 올리면 정작 그
	 * 화면은 여전히 예정으로 남아 더 혼란스럽기 때문이다. "가장 이른 회차 우선"은 목록 경로에서만
	 * 성립하는 규칙이다.
	 */
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void syncStateIfNeeded(Long bossRunId, Instant requestTime) {
		bossRunRepository.expireIfActiveAndEnded(bossRunId, requestTime);
		// 활성화는 보스 행 잠금을 잡는다. 전이가 밀려 있지 않은 평상시 상세 조회까지 잠금을 걸면
		// 같은 회차를 보는 요청들이 서로 직렬화되므로, 값싼 exists로 먼저 걸러 낸다.
		if (!bossRunRepository.isActivationDue(bossRunId, requestTime)) {
			return;
		}
		activateIfStarted(bossRunId, requestTime);
	}

	/** 목록 조회 직전에 밀린 전이를 처리한다. 호출부의 exists 가드를 통과한 요청만 들어온다. */
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void syncAllRunStates(Instant requestTime) {
		bossRunRepository.expireAllActiveRunsEndedAt(requestTime);
		// 상한을 넘겼는지 알아야 로그를 남길 수 있으므로 한 건 더 받아 온다.
		List<Long> candidates = bossRunRepository.findActivatableRunIds(requestTime,
				PageRequest.of(0, ACTIVATION_BATCH_LIMIT + 1));
		boolean deferred = candidates.size() > ACTIVATION_BATCH_LIMIT;
		List<Long> batch = deferred ? candidates.subList(0, ACTIVATION_BATCH_LIMIT) : candidates;
		for (Long runId : batch) {
			activateIfStarted(runId, requestTime);
		}
		if (deferred) {
			log.info("Activated {} boss runs in this pass and deferred the rest to a later read.",
					ACTIVATION_BATCH_LIMIT);
		}
	}

	/**
	 * 보스 행을 먼저 잠근다. uk_boss_runs_one_active_per_boss는 보스 단위 제약인데 회차 행 잠금은
	 * 회차 단위여서, 같은 보스의 서로 다른 두 회차를 다른 경로가 동시에 올리면 각자 다른 행을 잠근
	 * 채 둘 다 "진행 중 없음"으로 판단하고 제약 위반에 부딪힌다. 조회에 얹혀 도는 전이라 그 위반은
	 * 조회 요청의 500이 된다. 잠금 지점을 보스로 통일해 그 창을 닫는다.
	 *
	 * <p>회차 엔티티는 잠금을 잡은 뒤에 처음 읽어야 한다. 먼저 읽으면 잠금을 기다리는 동안 다른
	 * 트랜잭션이 커밋한 결과를 못 보고 영속성 컨텍스트에 담긴 낡은 상태를 다시 보게 된다.
	 */
	private void activateIfStarted(Long bossRunId, Instant now) {
		Long bossId = bossRunRepository.findBossIdByRunId(bossRunId).orElse(null);
		if (bossId == null || bossRepository.findByIdForUpdate(bossId).isEmpty()) {
			return;
		}
		BossRun run = bossRunRepository.findWithBossById(bossRunId).orElse(null);
		if (run == null || !isActivatable(run, now)) {
			return;
		}
		run.activate(now);
		// 같은 보스의 다음 후보가 이 변경을 보도록 즉시 반영한다.
		bossRunRepository.saveAndFlush(run);
	}

	/**
	 * 잠금을 잡은 뒤 다시 확인하는 조건. {@code BossRunRepository.ACTIVATABLE_PREDICATE}를 자바로
	 * 옮긴 것이라 한쪽만 고치면 안 된다. 조건을 하나라도 어긴 채 activate()를 부르면 예외가 나고,
	 * 그 예외는 이 REQUIRES_NEW 트랜잭션을 롤백 전용으로 만들어 전이를 곁다리로 수행하던 조회까지
	 * 함께 실패시킨다.
	 */
	private boolean isActivatable(BossRun run, Instant now) {
		return run.getStatus() == BossRunStatus.SCHEDULED
				&& run.getStartedAt() != null && !run.getStartedAt().isAfter(now)
				&& (run.getEndsAt() == null || run.getEndsAt().isAfter(now))
				&& !run.getPhases().isEmpty()
				&& !bossRunRepository.existsByBossIdAndStatus(run.getBoss().getId(), BossRunStatus.ACTIVE);
	}
}
