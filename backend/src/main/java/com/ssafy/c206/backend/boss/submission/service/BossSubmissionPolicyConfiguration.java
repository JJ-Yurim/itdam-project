package com.ssafy.c206.backend.boss.submission.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class BossSubmissionPolicyConfiguration {

	// 기본값을 여기 인라인으로 두면 application.properties의 값과 갈라져도 아무도 모른다.
	// 프로퍼티가 유일한 출처이고, 지워지면 기동이 실패해 즉시 드러난다.
	private static final String DAILY_LIMIT_PROPERTY = "${boss.submission.daily-limit}";

	@Bean
	BossSubmissionPolicy bossSubmissionPolicy(@Value(DAILY_LIMIT_PROPERTY) int dailySubmissionLimit) {
		return new BossSubmissionPolicy(dailySubmissionLimit);
	}
}
