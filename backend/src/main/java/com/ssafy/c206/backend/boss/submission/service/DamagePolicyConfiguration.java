package com.ssafy.c206.backend.boss.submission.service;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class DamagePolicyConfiguration {

	@Bean
	DamagePolicy damagePolicy() {
		return DamagePolicy.defaults();
	}
}
