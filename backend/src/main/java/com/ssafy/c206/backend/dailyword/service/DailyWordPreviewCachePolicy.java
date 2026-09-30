package com.ssafy.c206.backend.dailyword.service;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;

import com.ssafy.c206.backend.common.time.ServiceClock;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Calculates the browser cache lifetime without crossing a service-date boundary. */
@Component
@RequiredArgsConstructor
public class DailyWordPreviewCachePolicy {

	private final ServiceClock serviceClock;

	public Duration maxAge(LocalDate serviceDate, Instant requestTime) {
		var nextServiceDateStart = serviceDate.plusDays(1)
				.atStartOfDay(serviceClock.zoneId())
				.toInstant();
		long remainingSeconds = Duration.between(requestTime, nextServiceDateStart).toSeconds();
		return Duration.ofSeconds(Math.max(0, remainingSeconds));
	}
}
