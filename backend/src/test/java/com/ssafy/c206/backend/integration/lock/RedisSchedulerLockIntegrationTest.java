package com.ssafy.c206.backend.integration.lock;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.core.SimpleLock;
import net.javacrumbs.shedlock.provider.redis.spring.RedisLockProvider;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Tag("infra")
@Testcontainers
class RedisSchedulerLockIntegrationTest {

	@Container
	static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
			.withExposedPorts(6379);

	@Test
	void onlyOneBackendAcquiresTheSameLockAtATime() {
		LettuceConnectionFactory firstFactory = connectionFactory();
		LettuceConnectionFactory secondFactory = connectionFactory();
		LockProvider first = new RedisLockProvider(firstFactory, "c206:integration");
		LockProvider second = new RedisLockProvider(secondFactory, "c206:integration");
		LockConfiguration configuration = new LockConfiguration(
				Instant.now(), "sharedScheduler", Duration.ofSeconds(30), Duration.ZERO);

		try {
			Optional<SimpleLock> acquired = first.lock(configuration);
			assertThat(acquired).isPresent();
			assertThat(second.lock(configuration)).isEmpty();

			acquired.orElseThrow().unlock();
			Optional<SimpleLock> reacquired = second.lock(new LockConfiguration(
					Instant.now(), "sharedScheduler", Duration.ofSeconds(30), Duration.ZERO));
			assertThat(reacquired).isPresent();
			reacquired.orElseThrow().unlock();
		} finally {
			firstFactory.destroy();
			secondFactory.destroy();
		}
	}

	private static LettuceConnectionFactory connectionFactory() {
		LettuceConnectionFactory connectionFactory = new LettuceConnectionFactory(
				new RedisStandaloneConfiguration(REDIS.getHost(), REDIS.getMappedPort(6379)));
		connectionFactory.afterPropertiesSet();
		return connectionFactory;
	}
}
