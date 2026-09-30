package com.ssafy.c206.backend.boss.leaderboard.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.ssafy.c206.backend.boss.leaderboard.api.BossRunLeaderboardEntryResponse;
import com.ssafy.c206.backend.boss.leaderboard.api.BossRunLeaderboardResponse;
import com.ssafy.c206.backend.boss.repository.BossRunRepository;
import com.ssafy.c206.backend.boss.submission.repository.BossSubmissionRepository;
import com.ssafy.c206.backend.common.api.DomainException;
import com.ssafy.c206.backend.common.api.ErrorCode;
import com.ssafy.c206.backend.common.security.AuthenticatedUser;
import com.ssafy.c206.backend.common.security.AuthenticatedUsers;
import com.ssafy.c206.backend.integration.leaderboard.BossRunLeaderboardStore;
import com.ssafy.c206.backend.integration.leaderboard.UserDamageRank;
import com.ssafy.c206.backend.integration.leaderboard.UserDamageTotal;
import com.ssafy.c206.backend.user.service.UserQueryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 회차 기여도 리더보드 조회.
 *
 * <p>워밍업된 회차는 스토어(ZSET)에서 바로 읽는다. 콜드 회차(재시작·TTL 만료·첫 조회)는 DB
 * 집계로 응답하면서 그 결과로 스토어를 채워, 이후의 증분 반영이 붙을 기준을 만든다.
 *
 * <p>스토어는 캐시일 뿐 소스 오브 트루스는 DB다. 그래서 스토어 장애(Redis 단절)는 조회를
 * 실패시키지 않고 DB 집계 폴백으로 응답한다 — 증분 경로가 장애를 격리하는 것과 같은 원칙이다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BossRunLeaderboardService {

	static final int TOP_LIMIT = 10;

	private final BossRunRepository bossRunRepository;
	private final BossSubmissionRepository submissionRepository;
	private final BossRunLeaderboardStore leaderboardStore;
	private final UserQueryService userQueryService;

	@Transactional(readOnly = true)
	public BossRunLeaderboardResponse getLeaderboard(AuthenticatedUser principal, Long bossRunId) {
		long userId = AuthenticatedUsers.requireUserId(principal);
		if (!bossRunRepository.existsById(bossRunId)) {
			throw new DomainException(ErrorCode.NOT_FOUND);
		}
		try {
			if (leaderboardStore.isWarmedUp(bossRunId)) {
				return toResponse(bossRunId, leaderboardStore.top(bossRunId, TOP_LIMIT),
						leaderboardStore.rankOf(bossRunId, userId));
			}
		} catch (RuntimeException exception) {
			// 스토어가 죽어도 DB에 소스 오브 트루스가 그대로 있으므로 집계 폴백으로 응답한다.
			log.warn("boss_leaderboard_store_read_failed boss_run_id={}", bossRunId, exception);
		}
		List<UserDamageTotal> totals = aggregateFromDatabase(bossRunId);
		try {
			leaderboardStore.replaceAll(bossRunId, totals);
		} catch (RuntimeException exception) {
			// 워밍업은 부수 효과다. 실패해도 이번 응답은 집계 결과로 정상 반환하고, 스토어가
			// 복구되면 다음 콜드 조회나 병합 주기가 다시 채운다.
			log.warn("boss_leaderboard_warmup_failed boss_run_id={}", bossRunId, exception);
		}
		return toResponse(bossRunId, totals.subList(0, Math.min(TOP_LIMIT, totals.size())),
				rankIn(totals, userId));
	}

	/**
	 * DB 집계(소스 오브 트루스)로 스토어 전체를 덮어쓰고 집계 결과를 반환한다. 병합 주기 전용 —
	 * 여기서는 replaceAll 실패를 전파해 호출자(브로드캐스터)가 더티 마킹을 되살려 재시도하게 한다.
	 */
	@Transactional(readOnly = true)
	public List<UserDamageTotal> refreshFromDatabase(Long bossRunId) {
		List<UserDamageTotal> totals = aggregateFromDatabase(bossRunId);
		leaderboardStore.replaceAll(bossRunId, totals);
		return totals;
	}

	private List<UserDamageTotal> aggregateFromDatabase(Long bossRunId) {
		return submissionRepository.aggregateDamageTotals(bossRunId).stream()
				.map(row -> new UserDamageTotal(row.getUserId(), row.getTotalDamage()))
				.toList();
	}

	private BossRunLeaderboardResponse toResponse(Long bossRunId, List<UserDamageTotal> top,
			Optional<UserDamageRank> myRank) {
		Map<Long, String> nicknames = userQueryService.getNicknames(
				top.stream().map(UserDamageTotal::userId).toList());
		List<BossRunLeaderboardEntryResponse> entries = new ArrayList<>(top.size());
		for (int index = 0; index < top.size(); index++) {
			UserDamageTotal total = top.get(index);
			entries.add(new BossRunLeaderboardEntryResponse(index + 1L, total.userId(),
					nicknames.getOrDefault(total.userId(), ""), total.totalDamage()));
		}
		return new BossRunLeaderboardResponse(bossRunId, entries,
				myRank.map(rank -> new BossRunLeaderboardResponse.MyRank(rank.rank(), rank.totalDamage()))
						.orElse(null));
	}

	private static Optional<UserDamageRank> rankIn(List<UserDamageTotal> totals, long userId) {
		for (int index = 0; index < totals.size(); index++) {
			if (totals.get(index).userId() == userId) {
				return Optional.of(new UserDamageRank(index + 1L, totals.get(index).totalDamage()));
			}
		}
		return Optional.empty();
	}
}
