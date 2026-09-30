package com.ssafy.c206.backend.user.service;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import com.ssafy.c206.backend.common.time.ServiceClock;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

// Redis 없이 기동해야 하는 테스트·로컬 환경용 구현. TTL은 ServiceClock 기준으로 판정한다.
@Component
@ConditionalOnProperty(prefix = "email.verification", name = "store", havingValue = "memory", matchIfMissing = true)
@RequiredArgsConstructor
public class InMemoryEmailVerificationStore implements EmailVerificationStore {

	private record Entry(String value, Instant expiresAt) {
	}

	private final ServiceClock serviceClock;
	private final Map<String, Entry> entries = new ConcurrentHashMap<>();

	@Override
	public boolean tryStartCooldown(String email, Duration ttl) {
		AtomicBoolean started = new AtomicBoolean(false);
		entries.compute("cooldown:" + email, (key, current) -> {
			if (isLive(current)) {
				return current;
			}
			started.set(true);
			return new Entry("1", expiry(ttl));
		});
		return started.get();
	}

	@Override
	public void clearCooldown(String email) {
		entries.remove("cooldown:" + email);
	}

	@Override
	public long incrementIpSendCount(String clientIp, Duration window) {
		return incrementCounter("ip:" + clientIp, window);
	}

	@Override
	public void saveCode(String email, String code, Duration ttl) {
		entries.put("code:" + email, new Entry(code, expiry(ttl)));
		entries.remove("attempts:" + email);
	}

	@Override
	public Optional<String> findCode(String email) {
		return Optional.ofNullable(liveEntry("code:" + email)).map(Entry::value);
	}

	@Override
	public long incrementFailedAttempts(String email, Duration ttl) {
		return incrementCounter("attempts:" + email, ttl);
	}

	@Override
	public void removeCode(String email) {
		entries.remove("code:" + email);
		entries.remove("attempts:" + email);
	}

	@Override
	public void markVerified(String email, String token, Duration ttl) {
		entries.put("verified:" + email, new Entry(token, expiry(ttl)));
	}

	@Override
	public Optional<String> findVerifiedToken(String email) {
		return Optional.ofNullable(liveEntry("verified:" + email)).map(Entry::value);
	}

	@Override
	public void consumeVerified(String email) {
		entries.remove("verified:" + email);
	}

	private long incrementCounter(String key, Duration ttl) {
		Entry updated = entries.compute(key, (k, current) -> {
			if (!isLive(current)) {
				return new Entry("1", expiry(ttl));
			}
			return new Entry(String.valueOf(Long.parseLong(current.value()) + 1), current.expiresAt());
		});
		return Long.parseLong(updated.value());
	}

	private Instant expiry(Duration ttl) {
		return serviceClock.now().plus(ttl);
	}

	// 조회 시점에 만료 항목을 실제로 제거해 맵이 단조 증가하지 않게 한다.
	private Entry liveEntry(String key) {
		return entries.computeIfPresent(key, (k, entry) -> isLive(entry) ? entry : null);
	}

	private boolean isLive(Entry entry) {
		return entry != null && entry.expiresAt().isAfter(serviceClock.now());
	}
}
