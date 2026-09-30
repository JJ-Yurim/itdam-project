package com.ssafy.c206.backend.boss.leaderboard.service;

import com.ssafy.c206.backend.boss.submission.service.BossSubmissionAppliedEvent;
import com.ssafy.c206.backend.integration.leaderboard.BossRunDirtyStore;
import com.ssafy.c206.backend.integration.leaderboard.BossRunLeaderboardStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 커밋된 제출을 리더보드에 즉시 반영한다.
 *
 * <p>증분(addDamage)은 다리이고 재집계가 진실이다. 여기서의 증분은 실패해도(스토어 콜드·Redis
 * 장애) 제출 자체는 이미 커밋되어 있으므로, 더티 마킹만 살아 있으면 다음 병합 주기의 DB 재집계가
 * 그 제출을 포함해 덮어쓴다. 그래서 증분 실패는 로그로 끝내고 마킹은 항상 수행한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BossRunLeaderboardUpdater {

	private final BossRunLeaderboardStore leaderboardStore;
	private final BossRunDirtyStore dirtyStore;

	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	public void onSubmissionApplied(BossSubmissionAppliedEvent event) {
		// AFTER_COMMIT 리스너의 예외는 이미 커밋된 제출을 500으로 보이게 한다. 어떤 실패도 여기서 끝낸다.
		try {
			leaderboardStore.addDamage(event.bossRunId(), event.userId(), event.appliedDamage());
		} catch (RuntimeException exception) {
			log.warn("boss_leaderboard_increment_failed boss_run_id={} user_id={}",
					event.bossRunId(), event.userId(), exception);
		}
		try {
			dirtyStore.markDirty(event.bossRunId());
		} catch (RuntimeException exception) {
			log.error("boss_leaderboard_mark_dirty_failed boss_run_id={}", event.bossRunId(), exception);
		}
	}
}
