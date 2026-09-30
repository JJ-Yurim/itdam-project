package com.ssafy.c206.backend.integration.leaderboard;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "boss.leaderboard", name = "store", havingValue = "redis")
class RedisBossRunLeaderboardConfiguration {

	@Bean
	RedisBossRunLeaderboardStore bossRunLeaderboardStore(StringRedisTemplate redisTemplate) {
		return new RedisBossRunLeaderboardStore(redisTemplate);
	}

	@Bean
	RedisBossRunDirtyStore bossRunDirtyStore(StringRedisTemplate redisTemplate) {
		return new RedisBossRunDirtyStore(redisTemplate);
	}
}
