package com.ssafy.c206.backend.integration.leaderboard;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Tag("infra")
@Testcontainers
class RedisBossRunDirtyStoreIntegrationTest {

	@Container
	static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
			.withExposedPorts(6379);

	private static LettuceConnectionFactory firstConnectionFactory;
	private static LettuceConnectionFactory secondConnectionFactory;
	private static StringRedisTemplate firstTemplate;
	private static RedisBossRunDirtyStore first;
	private static RedisBossRunDirtyStore second;

	@BeforeAll
	static void setUp() {
		firstConnectionFactory = connectionFactory();
		secondConnectionFactory = connectionFactory();
		firstTemplate = redisTemplate(firstConnectionFactory);
		first = new RedisBossRunDirtyStore(firstTemplate);
		second = new RedisBossRunDirtyStore(redisTemplate(secondConnectionFactory));
	}

	@AfterEach
	void cleanUp() {
		firstTemplate.delete(List.of(
				RedisBossRunDirtyStore.PENDING_KEY,
				RedisBossRunDirtyStore.PROCESSING_KEY));
	}

	@AfterAll
	static void tearDown() {
		firstConnectionFactory.destroy();
		secondConnectionFactory.destroy();
	}

	@Test
	void marksFromOneBackendAreClaimedByTheOther() {
		first.markDirty(7L);

		assertThat(second.claimPending()).containsExactly(7L);
	}

	@Test
	void processingIsRestoredAfterTheClaimingBackendDies() {
		first.markDirty(7L);
		assertThat(first.claimPending()).containsExactly(7L);

		second.restoreProcessing();

		assertThat(second.claimPending()).containsExactly(7L);
	}

	@Test
	void aConcurrentNewMarkIsNotRemovedByCompletion() {
		first.markDirty(7L);
		assertThat(first.claimPending()).containsExactly(7L);
		second.markDirty(7L);

		first.complete(7L);

		assertThat(second.claimPending()).containsExactly(7L);
	}

	@Test
	void failureRequeuesForTheNextBackend() {
		first.markDirty(7L);
		assertThat(first.claimPending()).containsExactly(7L);

		first.requeue(7L);

		assertThat(second.claimPending()).containsExactly(7L);
	}

	private static LettuceConnectionFactory connectionFactory() {
		LettuceConnectionFactory connectionFactory = new LettuceConnectionFactory(
				new RedisStandaloneConfiguration(REDIS.getHost(), REDIS.getMappedPort(6379)));
		connectionFactory.afterPropertiesSet();
		return connectionFactory;
	}

	private static StringRedisTemplate redisTemplate(LettuceConnectionFactory connectionFactory) {
		StringRedisTemplate redisTemplate = new StringRedisTemplate(connectionFactory);
		redisTemplate.afterPropertiesSet();
		return redisTemplate;
	}
}
