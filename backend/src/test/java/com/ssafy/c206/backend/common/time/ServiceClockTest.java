package com.ssafy.c206.backend.common.time;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

import org.junit.jupiter.api.Test;

class ServiceClockTest {

	@Test
	void usesInjectedZoneAtDateBoundary() {
		Clock fixedClock = Clock.fixed(Instant.parse("2026-07-22T15:00:00Z"), ZoneId.of("UTC"));
		ServiceClock serviceClock = new ServiceClock(fixedClock, ZoneId.of("Asia/Seoul"));

		assertThat(serviceClock.now()).isEqualTo(Instant.parse("2026-07-22T15:00:00Z"));
		assertThat(serviceClock.today()).hasToString("2026-07-23");
		assertThat(serviceClock.zoneId()).isEqualTo(ZoneId.of("Asia/Seoul"));
	}
}
