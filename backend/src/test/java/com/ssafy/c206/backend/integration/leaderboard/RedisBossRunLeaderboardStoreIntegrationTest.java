package com.ssafy.c206.backend.integration.leaderboard;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterAll;
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

/**
 * ZSET 저장소가 실제 Redis에서 정합성 모델을 지키는지 검증한다. 워밍업 판정(키 존재)과
 * Lua 스크립트(콜드 스킵·원자 교체)는 인메모리 구현과 코드 경로가 완전히 달라 실 인프라로만
 * 확인할 수 있다.
 */
@Tag("infra")
@Testcontainers
class RedisBossRunLeaderboardStoreIntegrationTest {

	@Container
	static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
			.withExposedPorts(6379);

	private static LettuceConnectionFactory connectionFactory;
	private static RedisBossRunLeaderboardStore store;

	@BeforeAll
	static void setUp() {
		connectionFactory = new LettuceConnectionFactory(
				new RedisStandaloneConfiguration(REDIS.getHost(), REDIS.getMappedPort(6379)));
		connectionFactory.afterPropertiesSet();
		StringRedisTemplate redisTemplate = new StringRedisTemplate(connectionFactory);
		redisTemplate.afterPropertiesSet();
		store = new RedisBossRunLeaderboardStore(redisTemplate);
	}

	@AfterAll
	static void tearDown() {
		connectionFactory.destroy();
	}

	@Test
	void aColdIncrementDoesNotCreateTheKey() {
		long runId = System.nanoTime();

		// ZINCRBY가 콜드 키를 만들면 워밍업된 것처럼 보여, 이후 재집계가 같은 제출을 이중 계상한다.
		store.addDamage(runId, 10L, 90L);

		assertThat(store.isWarmedUp(runId)).isFalse();
		assertThat(store.top(runId, 10)).isEmpty();
	}

	@Test
	void incrementsAccumulateOnTopOfTheWarmedSnapshot() {
		long runId = System.nanoTime();
		store.replaceAll(runId, List.of(new UserDamageTotal(10L, 100L), new UserDamageTotal(20L, 50L)));

		store.addDamage(runId, 20L, 200L);

		assertThat(store.top(runId, 10)).containsExactly(
				new UserDamageTotal(20L, 250L),
				new UserDamageTotal(10L, 100L));
		assertThat(store.rankOf(runId, 20L)).contains(new UserDamageRank(1L, 250L));
		assertThat(store.rankOf(runId, 10L)).contains(new UserDamageRank(2L, 100L));
		assertThat(store.rankOf(runId, 99L)).isEmpty();
	}

	@Test
	void replaceAllOverwritesEveryDriftedEntryAtomically() {
		long runId = System.nanoTime();
		store.replaceAll(runId, List.of(new UserDamageTotal(10L, 999L), new UserDamageTotal(99L, 1L)));

		store.replaceAll(runId, List.of(new UserDamageTotal(10L, 100L)));

		assertThat(store.top(runId, 10)).containsExactly(new UserDamageTotal(10L, 100L));
		assertThat(store.rankOf(runId, 99L)).isEmpty();
	}

	@Test
	void tiedUsersGetDistinctPositionalRanksConsistentWithTop() {
		long runId = System.nanoTime();
		store.replaceAll(runId, List.of(new UserDamageTotal(10L, 200L), new UserDamageTotal(20L, 200L),
				new UserDamageTotal(30L, 100L)));

		// 순위 정의는 위치 순위(1, 2, 3)다 — 동점이라도 rankOf가 top()의 위치와 일치해야
		// 같은 응답 안에서 entries[].rank와 me.rank가 어긋나지 않는다.
		List<UserDamageTotal> top = store.top(runId, 10);
		assertThat(top).hasSize(3);
		for (int index = 0; index < top.size(); index++) {
			assertThat(store.rankOf(runId, top.get(index).userId()))
					.contains(new UserDamageRank(index + 1L, top.get(index).totalDamage()));
		}
	}

	@Test
	void aNonPositiveLimitReturnsAnEmptyList() {
		long runId = System.nanoTime();
		store.replaceAll(runId, List.of(new UserDamageTotal(10L, 100L)));

		// Redis의 (0, -1) 범위는 전체 집합이라, 가드가 없으면 0 이하가 "전부"로 뒤집힌다.
		assertThat(store.top(runId, 0)).isEmpty();
		assertThat(store.top(runId, -1)).isEmpty();
	}

	@Test
	void replaceAllHandlesMoreMembersThanOneUnpackChunk() {
		long runId = System.nanoTime();
		List<UserDamageTotal> totals = new ArrayList<>();
		for (long userId = 1; userId <= 1_200; userId++) {
			totals.add(new UserDamageTotal(userId, userId));
		}

		// 청크(500명) 경계를 넘는 교체에서 score·member 짝이 틀어지지 않아야 한다.
		store.replaceAll(runId, totals);

		assertThat(store.top(runId, 2)).containsExactly(
				new UserDamageTotal(1_200L, 1_200L), new UserDamageTotal(1_199L, 1_199L));
		assertThat(store.rankOf(runId, 1L)).contains(new UserDamageRank(1_200L, 1L));
	}

	@Test
	void replacingWithAnEmptyListReturnsTheRunToCold() {
		long runId = System.nanoTime();
		store.replaceAll(runId, List.of(new UserDamageTotal(10L, 100L)));

		store.replaceAll(runId, List.of());

		// 참여자가 없는 회차는 키가 없어야 하고, 조회는 매번 DB 집계(0행)로 응답하는 것이 맞다.
		assertThat(store.isWarmedUp(runId)).isFalse();
		assertThat(store.top(runId, 10)).isEmpty();
	}
}
