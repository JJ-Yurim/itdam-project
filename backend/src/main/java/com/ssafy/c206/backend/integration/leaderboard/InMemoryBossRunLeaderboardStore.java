package com.ssafy.c206.backend.integration.leaderboard;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Redis 없이 기동해야 하는 테스트·로컬 환경의 기본 구현.
 *
 * <p>단일 인스턴스 메모리라 재시작하면 사라지지만, 콜드 상태는 조회 시점의 DB 집계
 * 워밍업으로 복원되므로 동작은 유지된다.
 */
@Component
@ConditionalOnProperty(prefix = "boss.leaderboard", name = "store", havingValue = "in-memory",
		matchIfMissing = true)
public class InMemoryBossRunLeaderboardStore implements BossRunLeaderboardStore {

	private final Map<Long, Map<Long, Long>> totalsByRunId = new ConcurrentHashMap<>();

	@Override
	public void addDamage(long bossRunId, long userId, long damage) {
		// 콜드 회차에는 누적하지 않는다. 워밍업 전 증분을 받으면 이후 재집계가 그 제출을
		// 다시 세어 이중 계상이 된다.
		totalsByRunId.computeIfPresent(bossRunId, (runId, totals) -> {
			totals.merge(userId, damage, Long::sum);
			return totals;
		});
	}

	@Override
	public void replaceAll(long bossRunId, List<UserDamageTotal> totals) {
		Map<Long, Long> replaced = new ConcurrentHashMap<>();
		for (UserDamageTotal total : totals) {
			replaced.put(total.userId(), total.totalDamage());
		}
		totalsByRunId.put(bossRunId, replaced);
	}

	@Override
	public boolean isWarmedUp(long bossRunId) {
		return totalsByRunId.containsKey(bossRunId);
	}

	@Override
	public List<UserDamageTotal> top(long bossRunId, int limit) {
		if (limit <= 0) {
			return List.of();
		}
		return sorted(bossRunId).stream().limit(limit).toList();
	}

	@Override
	public Optional<UserDamageRank> rankOf(long bossRunId, long userId) {
		// top()과 같은 정렬에서 위치를 찾는다. 다른 기준(예: 공동 순위)으로 계산하면 동점일 때
		// 같은 응답의 entries[].rank와 me.rank가 서로 어긋난다.
		List<UserDamageTotal> sorted = sorted(bossRunId);
		for (int index = 0; index < sorted.size(); index++) {
			if (sorted.get(index).userId() == userId) {
				return Optional.of(new UserDamageRank(index + 1L, sorted.get(index).totalDamage()));
			}
		}
		return Optional.empty();
	}

	private List<UserDamageTotal> sorted(long bossRunId) {
		Map<Long, Long> totals = totalsByRunId.get(bossRunId);
		if (totals == null) {
			return List.of();
		}
		return totals.entrySet().stream()
				.map(entry -> new UserDamageTotal(entry.getKey(), entry.getValue()))
				.sorted(Comparator.comparingLong(UserDamageTotal::totalDamage).reversed()
						.thenComparingLong(UserDamageTotal::userId))
				.toList();
	}
}
