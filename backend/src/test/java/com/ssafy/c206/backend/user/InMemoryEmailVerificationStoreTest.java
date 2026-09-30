package com.ssafy.c206.backend.user;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import com.ssafy.c206.backend.common.time.ServiceClock;
import com.ssafy.c206.backend.user.service.InMemoryEmailVerificationStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class InMemoryEmailVerificationStoreTest {

	private static final String EMAIL = "ttl@example.com";

	private MutableClock clock;
	private InMemoryEmailVerificationStore store;

	@BeforeEach
	void setUp() {
		clock = new MutableClock(Instant.parse("2026-08-03T00:00:00Z"));
		store = new InMemoryEmailVerificationStore(new ServiceClock(clock, ZoneId.of("Asia/Seoul")));
	}

	@Test
	void codeExpiresAfterTtl() {
		store.saveCode(EMAIL, "123456", Duration.ofMinutes(5));
		assertThat(store.findCode(EMAIL)).contains("123456");

		clock.advance(Duration.ofMinutes(5).plusSeconds(1));
		assertThat(store.findCode(EMAIL)).isEmpty();
	}

	@Test
	void verifiedTokenExpiresAfterTtl() {
		store.markVerified(EMAIL, "token-1", Duration.ofMinutes(10));
		assertThat(store.findVerifiedToken(EMAIL)).contains("token-1");

		clock.advance(Duration.ofMinutes(10).plusSeconds(1));
		assertThat(store.findVerifiedToken(EMAIL)).isEmpty();
	}

	@Test
	void cooldownBlocksUntilExpiryThenRestarts() {
		assertThat(store.tryStartCooldown(EMAIL, Duration.ofSeconds(60))).isTrue();
		assertThat(store.tryStartCooldown(EMAIL, Duration.ofSeconds(60))).isFalse();

		clock.advance(Duration.ofSeconds(61));
		assertThat(store.tryStartCooldown(EMAIL, Duration.ofSeconds(60))).isTrue();
	}

	@Test
	void clearCooldownAllowsImmediateRestart() {
		assertThat(store.tryStartCooldown(EMAIL, Duration.ofSeconds(60))).isTrue();
		store.clearCooldown(EMAIL);
		assertThat(store.tryStartCooldown(EMAIL, Duration.ofSeconds(60))).isTrue();
	}

	@Test
	void ipCounterAccumulatesWithinWindowAndResetsAfter() {
		assertThat(store.incrementIpSendCount("10.0.0.1", Duration.ofHours(1))).isEqualTo(1);
		assertThat(store.incrementIpSendCount("10.0.0.1", Duration.ofHours(1))).isEqualTo(2);

		clock.advance(Duration.ofHours(1).plusSeconds(1));
		assertThat(store.incrementIpSendCount("10.0.0.1", Duration.ofHours(1))).isEqualTo(1);
	}

	@Test
	void savingNewCodeResetsFailedAttempts() {
		store.saveCode(EMAIL, "123456", Duration.ofMinutes(5));
		assertThat(store.incrementFailedAttempts(EMAIL, Duration.ofMinutes(5))).isEqualTo(1);
		assertThat(store.incrementFailedAttempts(EMAIL, Duration.ofMinutes(5))).isEqualTo(2);

		store.saveCode(EMAIL, "654321", Duration.ofMinutes(5));
		assertThat(store.incrementFailedAttempts(EMAIL, Duration.ofMinutes(5))).isEqualTo(1);
	}

	@Test
	void removeCodeClearsCodeAndAttempts() {
		store.saveCode(EMAIL, "123456", Duration.ofMinutes(5));
		store.incrementFailedAttempts(EMAIL, Duration.ofMinutes(5));

		store.removeCode(EMAIL);
		assertThat(store.findCode(EMAIL)).isEmpty();
		assertThat(store.incrementFailedAttempts(EMAIL, Duration.ofMinutes(5))).isEqualTo(1);
	}

	private static final class MutableClock extends Clock {

		private Instant current;

		private MutableClock(Instant start) {
			this.current = start;
		}

		void advance(Duration duration) {
			current = current.plus(duration);
		}

		@Override
		public ZoneId getZone() {
			return ZoneOffset.UTC;
		}

		@Override
		public Clock withZone(ZoneId zone) {
			return this;
		}

		@Override
		public Instant instant() {
			return current;
		}
	}
}
