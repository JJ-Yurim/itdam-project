package com.ssafy.c206.backend.boss.leaderboard.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import com.ssafy.c206.backend.boss.domain.BossRunStatus;
import com.ssafy.c206.backend.boss.submission.service.BossSubmissionAppliedEvent;
import com.ssafy.c206.backend.integration.leaderboard.InMemoryBossRunDirtyStore;
import com.ssafy.c206.backend.integration.leaderboard.BossRunLeaderboardStore;
import com.ssafy.c206.backend.integration.leaderboard.UserDamageRank;
import com.ssafy.c206.backend.integration.leaderboard.UserDamageTotal;
import org.junit.jupiter.api.Test;

class BossRunLeaderboardUpdaterTest {

	@Test
	void aFailingLeaderboardIncrementStillMarksTheCommittedRunDirty() {
		// AFTER_COMMIT 리스너의 예외는 이미 커밋된 제출을 사용자에게 500으로 보이게 한다.
		// 증분(Redis)이 죽어도 더티 마킹은 살아야 다음 병합 주기의 재집계가 이 제출을 포함한다.
		InMemoryBossRunDirtyStore dirtyStore = new InMemoryBossRunDirtyStore();
		BossRunLeaderboardUpdater updater = new BossRunLeaderboardUpdater(new ThrowingStore(), dirtyStore);

		assertThatCode(() -> updater.onSubmissionApplied(
				new BossSubmissionAppliedEvent(1L, 7L, 1, 910L, 1_000L, 90L,
						BigDecimal.valueOf(84), BossRunStatus.ACTIVE)))
				.doesNotThrowAnyException();
		assertThat(dirtyStore.claimPending()).containsExactly(1L);
	}

	private static final class ThrowingStore implements BossRunLeaderboardStore {
		@Override
		public void addDamage(long bossRunId, long userId, long damage) {
			throw new IllegalStateException("redis is down");
		}

		@Override
		public void replaceAll(long bossRunId, List<UserDamageTotal> totals) {
		}

		@Override
		public boolean isWarmedUp(long bossRunId) {
			return false;
		}

		@Override
		public List<UserDamageTotal> top(long bossRunId, int limit) {
			return List.of();
		}

		@Override
		public Optional<UserDamageRank> rankOf(long bossRunId, long userId) {
			return Optional.empty();
		}
	}
}
