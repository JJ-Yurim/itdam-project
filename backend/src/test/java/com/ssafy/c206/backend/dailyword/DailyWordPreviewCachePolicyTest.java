package com.ssafy.c206.backend.dailyword;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

import com.ssafy.c206.backend.common.time.ServiceClock;
import com.ssafy.c206.backend.dailyword.service.DailyWordPreviewCachePolicy;
import org.junit.jupiter.api.Test;

class DailyWordPreviewCachePolicyTest {

	private static final ZoneId SERVICE_ZONE = ZoneId.of("Asia/Seoul");

	@Test
	void maxAgeEndsAtTheNextServiceDate() {
		var policy = policy();

		assertThat(policy.maxAge(LocalDate.of(2026, 8, 4), Instant.parse("2026-08-04T06:00:00Z")))
				.isEqualTo(Duration.ofHours(9));
	}

	@Test
	void staleResponseIsNotCachedPastItsServiceDate() {
		var policy = policy();

		assertThat(policy.maxAge(LocalDate.of(2026, 8, 4), Instant.parse("2026-08-04T15:00:01Z")))
				.isZero();
	}

	private DailyWordPreviewCachePolicy policy() {
		Clock clock = Clock.fixed(Instant.EPOCH, ZoneId.of("UTC"));
		return new DailyWordPreviewCachePolicy(new ServiceClock(clock, SERVICE_ZONE));
	}
}
