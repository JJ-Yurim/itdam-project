package com.ssafy.c206.backend.common.config;

import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.provider.redis.spring.RedisLockProvider;
import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.connection.RedisConnectionFactory;

@Configuration(proxyBeanMethods = false)
@Profile({"develop", "prod"})
@EnableSchedulerLock(defaultLockAtMostFor = "PT2H")
class SchedulerLockConfiguration {

	@Bean
	LockProvider lockProvider(RedisConnectionFactory connectionFactory,
			@Value("${app.scheduler-lock.environment}") String environment) {
		return new RedisLockProvider(connectionFactory, environment);
	}
}
