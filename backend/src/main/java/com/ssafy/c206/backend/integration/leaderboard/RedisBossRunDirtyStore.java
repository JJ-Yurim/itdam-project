package com.ssafy.c206.backend.integration.leaderboard;

import java.util.List;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

public class RedisBossRunDirtyStore implements BossRunDirtyStore {

	static final String PENDING_KEY = "c206:boss:leaderboard:dirty:pending";
	static final String PROCESSING_KEY = "c206:boss:leaderboard:dirty:processing";

	private static final RedisScript<List> CLAIM_PENDING = RedisScript.of(
			"local values = redis.call('SMEMBERS', KEYS[1]) "
					+ "for _, value in ipairs(values) do redis.call('SADD', KEYS[2], value) end "
					+ "redis.call('DEL', KEYS[1]) "
					+ "return values", List.class);

	private static final RedisScript<Long> RESTORE_PROCESSING = RedisScript.of(
			"local values = redis.call('SMEMBERS', KEYS[2]) "
					+ "for _, value in ipairs(values) do redis.call('SADD', KEYS[1], value) end "
					+ "redis.call('DEL', KEYS[2]) "
					+ "return #values", Long.class);

	private static final RedisScript<Long> REQUEUE = RedisScript.of(
			"redis.call('SADD', KEYS[1], ARGV[1]) "
					+ "redis.call('SREM', KEYS[2], ARGV[1]) "
					+ "return 1", Long.class);

	private final StringRedisTemplate redisTemplate;

	public RedisBossRunDirtyStore(StringRedisTemplate redisTemplate) {
		this.redisTemplate = redisTemplate;
	}

	@Override
	public void markDirty(long bossRunId) {
		redisTemplate.opsForSet().add(PENDING_KEY, Long.toString(bossRunId));
	}

	@Override
	public void restoreProcessing() {
		redisTemplate.execute(RESTORE_PROCESSING, List.of(PENDING_KEY, PROCESSING_KEY));
	}

	@Override
	@SuppressWarnings("unchecked")
	public List<Long> claimPending() {
		List<String> values = (List<String>) (List<?>) redisTemplate.execute(
				CLAIM_PENDING, List.of(PENDING_KEY, PROCESSING_KEY));
		return values == null ? List.of() : values.stream().map(Long::parseLong).toList();
	}

	@Override
	public void complete(long bossRunId) {
		redisTemplate.opsForSet().remove(PROCESSING_KEY, Long.toString(bossRunId));
	}

	@Override
	public void requeue(long bossRunId) {
		redisTemplate.execute(REQUEUE, List.of(PENDING_KEY, PROCESSING_KEY), Long.toString(bossRunId));
	}
}
