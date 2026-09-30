package com.ssafy.c206.backend.integration.leaderboard;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

class InMemoryBossRunLeaderboardStoreTest {

	@Test
	void coldIncrementsAreSkippedSoLaterAggregationDoesNotDoubleCount() {
		InMemoryBossRunLeaderboardStore store = new InMemoryBossRunLeaderboardStore();

		// 콜드 증분이 반영되면, 그 제출은 이미 커밋되어 있어 이후 재집계가 같은 제출을 다시 센다.
		store.addDamage(1L, 10L, 90L);

		assertThat(store.isWarmedUp(1L)).isFalse();
		assertThat(store.top(1L, 10)).isEmpty();
		assertThat(store.rankOf(1L, 10L)).isEmpty();
	}

	@Test
	void incrementsAccumulateOnTopOfTheWarmedSnapshot() {
		InMemoryBossRunLeaderboardStore store = new InMemoryBossRunLeaderboardStore();
		store.replaceAll(1L, List.of(new UserDamageTotal(10L, 100L), new UserDamageTotal(20L, 50L)));

		store.addDamage(1L, 20L, 200L);
		store.addDamage(1L, 30L, 10L);

		assertThat(store.top(1L, 10)).containsExactly(
				new UserDamageTotal(20L, 250L),
				new UserDamageTotal(10L, 100L),
				new UserDamageTotal(30L, 10L));
	}

	@Test
	void replaceAllOverwritesEveryDriftedEntry() {
		InMemoryBossRunLeaderboardStore store = new InMemoryBossRunLeaderboardStore();
		store.replaceAll(1L, List.of(new UserDamageTotal(10L, 999L), new UserDamageTotal(99L, 1L)));

		// 재집계는 증분 경로가 만든 어떤 드리프트도 권위 있게 덮어쓴다. 빠진 사용자(99)도 사라져야 한다.
		store.replaceAll(1L, List.of(new UserDamageTotal(10L, 100L)));

		assertThat(store.top(1L, 10)).containsExactly(new UserDamageTotal(10L, 100L));
		assertThat(store.rankOf(1L, 99L)).isEmpty();
	}

	@Test
	void rankIsThePositionInTheSameOrderingAsTop() {
		InMemoryBossRunLeaderboardStore store = new InMemoryBossRunLeaderboardStore();
		store.replaceAll(1L, List.of(new UserDamageTotal(10L, 300L), new UserDamageTotal(20L, 200L),
				new UserDamageTotal(30L, 100L)));

		assertThat(store.rankOf(1L, 10L)).contains(new UserDamageRank(1L, 300L));
		assertThat(store.rankOf(1L, 30L)).contains(new UserDamageRank(3L, 100L));
		assertThat(store.rankOf(1L, 40L)).isEmpty();
	}

	@Test
	void tiedUsersGetDistinctPositionalRanksConsistentWithTop() {
		// 공동 순위(1, 1, 3)로 계산하면 같은 응답 안에서 entries[].rank(위치 기준)와 me.rank가
		// 어긋나고, 위치 순위인 Redis 구현과도 달라진다. 반드시 top()과 같은 정렬의 위치여야 한다.
		InMemoryBossRunLeaderboardStore store = new InMemoryBossRunLeaderboardStore();
		store.replaceAll(1L, List.of(new UserDamageTotal(10L, 200L), new UserDamageTotal(20L, 200L),
				new UserDamageTotal(30L, 100L)));

		assertThat(store.top(1L, 10)).containsExactly(new UserDamageTotal(10L, 200L),
				new UserDamageTotal(20L, 200L), new UserDamageTotal(30L, 100L));
		assertThat(store.rankOf(1L, 10L)).contains(new UserDamageRank(1L, 200L));
		assertThat(store.rankOf(1L, 20L)).contains(new UserDamageRank(2L, 200L));
		assertThat(store.rankOf(1L, 30L)).contains(new UserDamageRank(3L, 100L));
	}

	@Test
	void aNonPositiveLimitReturnsAnEmptyList() {
		InMemoryBossRunLeaderboardStore store = new InMemoryBossRunLeaderboardStore();
		store.replaceAll(1L, List.of(new UserDamageTotal(10L, 100L)));

		assertThat(store.top(1L, 0)).isEmpty();
		assertThat(store.top(1L, -1)).isEmpty();
	}

	@Test
	void runsAreIsolatedFromEachOther() {
		InMemoryBossRunLeaderboardStore store = new InMemoryBossRunLeaderboardStore();
		store.replaceAll(1L, List.of(new UserDamageTotal(10L, 100L)));

		store.addDamage(2L, 10L, 500L);

		assertThat(store.isWarmedUp(2L)).isFalse();
		assertThat(store.top(1L, 10)).containsExactly(new UserDamageTotal(10L, 100L));
	}
}
