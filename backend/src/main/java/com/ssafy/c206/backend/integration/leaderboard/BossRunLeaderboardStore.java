package com.ssafy.c206.backend.integration.leaderboard;

import java.util.List;
import java.util.Optional;

/**
 * 회차별 기여도 순위 저장소.
 *
 * <p>여기 담긴 값은 소스 오브 트루스가 아니라 {@code boss_submissions}의 사용자별
 * {@code sum(final_damage)}로 언제든 재구성 가능한 캐시다. 정합성 모델은 다음과 같다.
 *
 * <ul>
 * <li>{@link #addDamage}는 제출 커밋 직후의 즉시 반영용 증분이다. 저장소가 아직 워밍업되지
 * 않았으면 조용히 건너뛴다 — 그 제출은 이미 커밋되어 있으므로 이후 재집계가 포함한다.</li>
 * <li>{@link #replaceAll}은 DB 집계 결과로 전체를 덮어쓰는 권위 있는 쓰기다. 증분과 워밍업의
 * 경합이 만드는 드리프트는 다음 재집계에서 이 쓰기로 수렴한다.</li>
 * </ul>
 *
 * <p>순위는 공동 순위(1, 1, 3)가 아니라 정렬 위치 순위(1, 2, 3)다. {@link #rankOf}는 반드시
 * {@link #top}과 같은 정렬에서의 위치를 반환해야 한다 — 기준이 다르면 동점일 때 목록의 순위와
 * 내 순위가 같은 응답 안에서 어긋난다. 동점끼리의 정렬 순서 자체는 구현마다 다를 수 있고
 * (데미지 내림차순까지만 보장), 회차가 끝나면 구현이 TTL로 항목을 정리한다.
 */
public interface BossRunLeaderboardStore {

	/** 워밍업된 회차에만 데미지를 누적한다. 콜드 상태면 아무것도 하지 않는다. */
	void addDamage(long bossRunId, long userId, long damage);

	/** DB 집계 결과로 회차 전체를 원자적으로 교체한다. 빈 목록이면 회차 항목을 비운다. */
	void replaceAll(long bossRunId, List<UserDamageTotal> totals);

	/** 이 회차가 워밍업(최초 집계 반영)됐는지. 콜드면 호출자는 DB 집계로 응답해야 한다. */
	boolean isWarmedUp(long bossRunId);

	/** 누적 데미지 내림차순 상위 limit명. limit이 0 이하면 빈 목록이다. */
	List<UserDamageTotal> top(long bossRunId, int limit);

	/** 해당 사용자의 정렬 위치 순위(1부터)와 누적 데미지. 참여하지 않았으면 비어 있다. */
	Optional<UserDamageRank> rankOf(long bossRunId, long userId);
}
