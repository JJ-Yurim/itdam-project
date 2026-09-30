package com.ssafy.c206.backend.boss.leaderboard.service;

import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import com.ssafy.c206.backend.integration.leaderboard.BossRunDirtyStore;
import com.ssafy.c206.backend.integration.leaderboard.UserDamageTotal;
import com.ssafy.c206.backend.integration.stream.BossRunEventStream;
import com.ssafy.c206.backend.integration.stream.BossRunLeaderboardStreamEvent;
import com.ssafy.c206.backend.user.service.UserQueryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 더티 회차를 주기적으로 DB 재집계로 수렴시키고, 그 스냅숏을 SSE 구독자에게 병합 전송한다.
 *
 * <p>제출마다 순위 전체를 브로드캐스트하면 순위 전송량이 제출 TPS에 비례해 커진다. 병합 주기
 * 안의 제출 몇 건이든 스냅숏 한 번으로 나가므로 전송량 상한이 주기당 1회로 고정된다.
 *
 * <p>이 재집계가 증분 경로의 모든 드리프트(콜드 스킵·Redis 장애·워밍업 경합)를 권위 있게
 * 덮어쓰는 수렴 지점이다. 한 주기의 재집계가 실패하면 더티 마킹을 되살려 다음 주기에 재시도한다.
 *
	 * <p>더티 회차는 공유 pending/processing 저장소에서 고정 스냅숏으로 클레임한다. 배포 환경에서는
	 * Redis가 상태를 보존하고 ShedLock을 획득한 한 인스턴스만 드레인한다. 처리 중 장애가 나면 processing
	 * 항목을 다음 주기에 pending으로 복구하고, 재집계 실패 항목은 같은 호출이 아니라 다음 주기에 재시도한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BossRunLeaderboardBroadcaster {

	static final int BROADCAST_TOP_LIMIT = 10;

	private final BossRunLeaderboardService leaderboardService;
	private final BossRunEventStream eventStream;
	private final UserQueryService userQueryService;
	private final BossRunDirtyStore dirtyStore;

	/** 복구된 처리 항목과 현재 pending 스냅숏을 한 번씩 재집계해 구독자에게 전송한다. */
	@Scheduled(fixedDelayString = "${boss.leaderboard.broadcast-delay-ms:1000}")
	@SchedulerLock(name = "bossLeaderboardBroadcast", lockAtLeastFor = "PT1S", lockAtMostFor = "PT30S")
	public void broadcastDirtyRuns() {
		dirtyStore.restoreProcessing();
		for (Long bossRunId : dirtyStore.claimPending()) {
			// 드레인을 재집계보다 먼저 한다. 나중에 지우면 재집계 중 도착한 제출의 마킹까지 지워
			// 그 제출이 다음 주기 재집계에서 빠질 수 있다. 먼저 지우면 최악이 중복 재집계다.
			try {
				List<UserDamageTotal> totals = leaderboardService.refreshFromDatabase(bossRunId);
				eventStream.publishLeaderboard(
						new BossRunLeaderboardStreamEvent(bossRunId, toEntries(totals, BROADCAST_TOP_LIMIT)));
				dirtyStore.complete(bossRunId);
			} catch (RuntimeException exception) {
				log.warn("boss_leaderboard_broadcast_failed boss_run_id={}", bossRunId, exception);
				dirtyStore.requeue(bossRunId);
			}
		}
	}

	/** 집계 결과 상위 limit명에 순위와 닉네임을 붙인다. 순위는 정렬 위치 기준 1부터다. */
	List<BossRunLeaderboardStreamEvent.Entry> toEntries(List<UserDamageTotal> totals, int limit) {
		List<UserDamageTotal> top = totals.subList(0, Math.min(limit, totals.size()));
		Map<Long, String> nicknames = userQueryService.getNicknames(
				top.stream().map(UserDamageTotal::userId).toList());
		return IntStream.range(0, top.size())
				.mapToObj(index -> new BossRunLeaderboardStreamEvent.Entry(top.get(index).userId(),
						nicknames.getOrDefault(top.get(index).userId(), ""),
						top.get(index).totalDamage(), index + 1L))
				.toList();
	}
}
