package com.ssafy.c206.backend.common.time;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public final class ServiceClock {

	@NonNull
	private final Clock clock;
	@NonNull
	private final ZoneId zoneId;

	public Instant now() {
		return Instant.now(clock);
	}

	public LocalDate today() {
		return LocalDate.ofInstant(now(), zoneId);
	}

	public LocalDate dateAt(Instant instant) {
		return LocalDate.ofInstant(instant, zoneId);
	}

	public ZoneId zoneId() {
		return zoneId;
	}
}
