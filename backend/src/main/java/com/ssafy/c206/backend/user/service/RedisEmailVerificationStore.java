package com.ssafy.c206.backend.user.service;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

// 인증 상태는 users 테이블이 아닌 휘발성 도메인 상태라 어댑터도 user/service에 함께 둔다(integration/은 외부 시스템 '호출' 어댑터).
@Component
@ConditionalOnProperty(prefix = "email.verification", name = "store", havingValue = "redis")
@RequiredArgsConstructor
public class RedisEmailVerificationStore implements EmailVerificationStore {

	private static final String CODE_PREFIX = "email-verification:code:";
	private static final String ATTEMPTS_PREFIX = "email-verification:attempts:";
	private static final String COOLDOWN_PREFIX = "email-verification:cooldown:";
	private static final String IP_PREFIX = "email-verification:ip:";
	// v2: 값이 "1"이던 구버전 표식과 키 공간을 분리한다. 프리픽스를 공유하면 배포 직후 남은 구 표식(TTL 10분)을
	// verificationToken="1"로 통과시킬 수 있다. 구 표식은 TTL로 자연 소멸한다.
	private static final String VERIFIED_PREFIX = "email-verification:verified:v2:";

	// INCR와 EXPIRE 사이에서 연결이 끊기면 TTL 없는 카운터가 영구히 남으므로 한 스크립트로 묶는다.
	private static final RedisScript<Long> INCREMENT_WITH_TTL_ON_FIRST = RedisScript.of(
			"local count = redis.call('INCR', KEYS[1]) "
					+ "if count == 1 then redis.call('PEXPIRE', KEYS[1], ARGV[1]) end "
					+ "return count", Long.class);

	private final StringRedisTemplate redisTemplate;

	@Override
	public boolean tryStartCooldown(String email, Duration ttl) {
		return Boolean.TRUE.equals(
				redisTemplate.opsForValue().setIfAbsent(COOLDOWN_PREFIX + email, "1", ttl));
	}

	@Override
	public void clearCooldown(String email) {
		redisTemplate.delete(COOLDOWN_PREFIX + email);
	}

	@Override
	public long incrementIpSendCount(String clientIp, Duration window) {
		return incrementWithTtlOnFirst(IP_PREFIX + clientIp, window);
	}

	@Override
	public void saveCode(String email, String code, Duration ttl) {
		redisTemplate.opsForValue().set(CODE_PREFIX + email, code, ttl);
		redisTemplate.delete(ATTEMPTS_PREFIX + email);
	}

	@Override
	public Optional<String> findCode(String email) {
		return Optional.ofNullable(redisTemplate.opsForValue().get(CODE_PREFIX + email));
	}

	@Override
	public long incrementFailedAttempts(String email, Duration ttl) {
		return incrementWithTtlOnFirst(ATTEMPTS_PREFIX + email, ttl);
	}

	@Override
	public void removeCode(String email) {
		redisTemplate.delete(CODE_PREFIX + email);
		redisTemplate.delete(ATTEMPTS_PREFIX + email);
	}

	@Override
	public void markVerified(String email, String token, Duration ttl) {
		redisTemplate.opsForValue().set(VERIFIED_PREFIX + email, token, ttl);
	}

	@Override
	public Optional<String> findVerifiedToken(String email) {
		return Optional.ofNullable(redisTemplate.opsForValue().get(VERIFIED_PREFIX + email));
	}

	@Override
	public void consumeVerified(String email) {
		redisTemplate.delete(VERIFIED_PREFIX + email);
	}

	private long incrementWithTtlOnFirst(String key, Duration ttl) {
		Long count = redisTemplate.execute(INCREMENT_WITH_TTL_ON_FIRST, List.of(key),
				String.valueOf(ttl.toMillis()));
		return count == null ? 1L : count;
	}
}
