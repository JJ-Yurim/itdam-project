package com.ssafy.c206.backend.common.time;

import java.time.Clock;
import java.time.ZoneId;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ServiceTimeConfiguration {

	@Bean
	ZoneId serviceZoneId(@Value("${service.zone-id:Asia/Seoul}") String zoneId) {
		return ZoneId.of(zoneId);
	}

	@Bean
	Clock serviceClockSource(ZoneId serviceZoneId) {
		return Clock.system(serviceZoneId);
	}

	@Bean
	ServiceClock serviceClock(Clock serviceClockSource, ZoneId serviceZoneId) {
		return new ServiceClock(serviceClockSource, serviceZoneId);
	}
}
