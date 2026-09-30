package com.ssafy.c206.backend.boss.leaderboard.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.ssafy.c206.backend.boss.leaderboard.api.BossRunLeaderboardResponse;
import com.ssafy.c206.backend.boss.repository.BossRunRepository;
import com.ssafy.c206.backend.boss.submission.repository.BossSubmissionRepository;
import com.ssafy.c206.backend.common.security.AuthenticatedUser;
import com.ssafy.c206.backend.integration.leaderboard.BossRunLeaderboardStore;
import com.ssafy.c206.backend.integration.leaderboard.UserDamageRank;
import com.ssafy.c206.backend.integration.leaderboard.UserDamageTotal;
import com.ssafy.c206.backend.user.service.UserQueryService;
import org.junit.jupiter.api.Test;

class BossRunLeaderboardServiceTest {

	@Test
	void aDeadStoreFallsBackToTheDatabaseAggregationInsteadOfFailingTheRead() {
		// 스토어는 캐시고 소스 오브 트루스는 DB다. Redis가 죽었다고 조회가 500이면, 증분 경로만
		// 격리하고 조회 경로는 격리하지 않은 반쪽 격리가 된다.
		BossRunRepository runRepository = mock(BossRunRepository.class);
		BossSubmissionRepository submissionRepository = mock(BossSubmissionRepository.class);
		UserQueryService userQueryService = mock(UserQueryService.class);
		when(runRepository.existsById(1L)).thenReturn(true);
		when(submissionRepository.aggregateDamageTotals(1L)).thenReturn(List.of(
				row(7L, 190L), row(8L, 90L)));
		when(userQueryService.getNicknames(anyCollection())).thenReturn(Map.of(7L, "일등", 8L, "이등"));
		BossRunLeaderboardService service = new BossRunLeaderboardService(
				runRepository, submissionRepository, new DeadStore(), userQueryService);

		BossRunLeaderboardResponse response = service.getLeaderboard(new AuthenticatedUser(8L, "b@t.t"), 1L);

		assertThat(response.entries()).hasSize(2);
		assertThat(response.entries().get(0).userId()).isEqualTo(7L);
		assertThat(response.entries().get(0).rank()).isEqualTo(1L);
		assertThat(response.me().rank()).isEqualTo(2L);
		assertThat(response.me().totalDamage()).isEqualTo(90L);
	}

	private static BossSubmissionRepository.UserDamageTotalRow row(long userId, long totalDamage) {
		return new BossSubmissionRepository.UserDamageTotalRow() {
			@Override
			public Long getUserId() {
				return userId;
			}

			@Override
			public Long getTotalDamage() {
				return totalDamage;
			}
		};
	}

	/** 모든 접근이 실패하는 스토어 — Redis 단절을 재현한다. */
	private static final class DeadStore implements BossRunLeaderboardStore {
		@Override
		public void addDamage(long bossRunId, long userId, long damage) {
			throw new IllegalStateException("redis is down");
		}

		@Override
		public void replaceAll(long bossRunId, List<UserDamageTotal> totals) {
			throw new IllegalStateException("redis is down");
		}

		@Override
		public boolean isWarmedUp(long bossRunId) {
			throw new IllegalStateException("redis is down");
		}

		@Override
		public List<UserDamageTotal> top(long bossRunId, int limit) {
			throw new IllegalStateException("redis is down");
		}

		@Override
		public Optional<UserDamageRank> rankOf(long bossRunId, long userId) {
			throw new IllegalStateException("redis is down");
		}
	}
}
