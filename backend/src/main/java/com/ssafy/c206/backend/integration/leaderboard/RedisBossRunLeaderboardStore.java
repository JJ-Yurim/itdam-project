package com.ssafy.c206.backend.integration.leaderboard;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.data.redis.core.script.RedisScript;

/**
 * Redis Sorted Set 기반 순위 저장소. 배포 환경(develop·prod)에서 사용한다.
 *
 * <p>키 존재 자체가 워밍업 표식이다. 별도 마커 키를 두면 마커와 본 키가 어긋나는 상태가
 * 생긴다. 참여자가 없는 회차는 키가 없고, 그 경우 조회가 매번 DB 집계(0행)로 응답하는
 * 것이 맞는 동작이다.
 */
public class RedisBossRunLeaderboardStore implements BossRunLeaderboardStore {

	private static final String KEY_PREFIX = "boss:run:lb:";
	/** 회차 수명(최대 수 주)보다 넉넉히 길고, 종료된 회차는 갱신이 멈춰 이 TTL로 정리된다. */
	private static final Duration TTL = Duration.ofDays(60);

	// 콜드 키에 ZINCRBY하면 그 증분이 키를 만들어 워밍업된 것처럼 보이고, 이후 재집계가 같은
	// 제출을 다시 세어 이중 계상이 된다. 존재할 때만 누적하도록 확인과 누적을 원자로 묶는다.
	private static final RedisScript<Long> INCREMENT_IF_WARMED = RedisScript.of(
			"if redis.call('EXISTS', KEYS[1]) == 0 then return 0 end "
					+ "redis.call('ZINCRBY', KEYS[1], ARGV[1], ARGV[2]) "
					+ "redis.call('PEXPIRE', KEYS[1], ARGV[3]) "
					+ "return 1", Long.class);

	// DEL과 ZADD 사이에 조회가 끼면 빈 순위가 보이므로 원자로 교체한다.
	// 멤버마다 ZADD를 따로 부르면 참가자 수만큼 명령이 실행되어 단일 스레드인 Redis를 그만큼
	// 붙잡는다. unpack으로 한 번에 넣되, Lua unpack의 인자 수 한계(약 8천)를 넘지 않게
	// 1000개(500명) 단위로 나눈다 — 청크 크기가 짝수라 score·member 짝은 유지된다.
	// ARGV = [ttlMillis, score1, member1, score2, member2, ...]
	private static final RedisScript<Long> REPLACE_ALL = RedisScript.of(
			"redis.call('DEL', KEYS[1]) "
					+ "for i = 2, #ARGV, 1000 do "
					+ "redis.call('ZADD', KEYS[1], unpack(ARGV, i, math.min(i + 999, #ARGV))) "
					+ "end "
					+ "if redis.call('EXISTS', KEYS[1]) == 1 then redis.call('PEXPIRE', KEYS[1], ARGV[1]) end "
					+ "return 1", Long.class);

	private final StringRedisTemplate redisTemplate;

	public RedisBossRunLeaderboardStore(StringRedisTemplate redisTemplate) {
		this.redisTemplate = redisTemplate;
	}

	@Override
	public void addDamage(long bossRunId, long userId, long damage) {
		redisTemplate.execute(INCREMENT_IF_WARMED, List.of(key(bossRunId)),
				String.valueOf(damage), String.valueOf(userId), String.valueOf(TTL.toMillis()));
	}

	@Override
	public void replaceAll(long bossRunId, List<UserDamageTotal> totals) {
		List<String> args = new ArrayList<>(1 + totals.size() * 2);
		args.add(String.valueOf(TTL.toMillis()));
		for (UserDamageTotal total : totals) {
			args.add(String.valueOf(total.totalDamage()));
			args.add(String.valueOf(total.userId()));
		}
		redisTemplate.execute(REPLACE_ALL, List.of(key(bossRunId)), args.toArray());
	}

	@Override
	public boolean isWarmedUp(long bossRunId) {
		return Boolean.TRUE.equals(redisTemplate.hasKey(key(bossRunId)));
	}

	@Override
	public List<UserDamageTotal> top(long bossRunId, int limit) {
		if (limit <= 0) {
			// Redis의 (0, -1) 범위는 전체 집합이라, 가드가 없으면 0 이하가 "전부"로 뒤집힌다.
			return List.of();
		}
		Set<ZSetOperations.TypedTuple<String>> tuples =
				redisTemplate.opsForZSet().reverseRangeWithScores(key(bossRunId), 0, limit - 1L);
		if (tuples == null) {
			return List.of();
		}
		List<UserDamageTotal> totals = new ArrayList<>(tuples.size());
		for (ZSetOperations.TypedTuple<String> tuple : tuples) {
			if (tuple.getValue() == null || tuple.getScore() == null) {
				continue;
			}
			totals.add(new UserDamageTotal(Long.parseLong(tuple.getValue()), tuple.getScore().longValue()));
		}
		return totals;
	}

	@Override
	public Optional<UserDamageRank> rankOf(long bossRunId, long userId) {
		String member = String.valueOf(userId);
		Long rank = redisTemplate.opsForZSet().reverseRank(key(bossRunId), member);
		if (rank == null) {
			return Optional.empty();
		}
		Double score = redisTemplate.opsForZSet().score(key(bossRunId), member);
		if (score == null) {
			return Optional.empty();
		}
		return Optional.of(new UserDamageRank(rank + 1, score.longValue()));
	}

	private static String key(long bossRunId) {
		return KEY_PREFIX + bossRunId;
	}
}
