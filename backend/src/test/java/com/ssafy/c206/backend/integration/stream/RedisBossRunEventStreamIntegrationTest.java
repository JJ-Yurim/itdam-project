package com.ssafy.c206.backend.integration.stream;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * Redis 채널이 실제 Redis에서 발행-수신 왕복과 시퀀스 단조 증가를 지키는지 검증한다.
 * 인프로세스 구현과 달리 직렬화·pub/sub 구독이 끼므로 실 인프라로만 확인할 수 있다.
 */
@Tag("infra")
@Testcontainers
class RedisBossRunEventStreamIntegrationTest {

	@Container
	static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
			.withExposedPorts(6379);

	private static LettuceConnectionFactory connectionFactory;
	private static RedisMessageListenerContainer listenerContainer;
	private static RedisBossRunEventStream stream;

	@BeforeAll
	static void setUp() {
		connectionFactory = new LettuceConnectionFactory(
				new RedisStandaloneConfiguration(REDIS.getHost(), REDIS.getMappedPort(6379)));
		connectionFactory.afterPropertiesSet();
		StringRedisTemplate redisTemplate = new StringRedisTemplate(connectionFactory);
		redisTemplate.afterPropertiesSet();
		stream = new RedisBossRunEventStream(redisTemplate, new ObjectMapper());
		listenerContainer = new RedisMessageListenerContainer();
		listenerContainer.setConnectionFactory(connectionFactory);
		listenerContainer.addMessageListener(stream, new ChannelTopic(RedisBossRunEventStream.CHANNEL));
		listenerContainer.addMessageListener(stream, new ChannelTopic(RedisBossRunEventStream.LEADERBOARD_CHANNEL));
		listenerContainer.afterPropertiesSet();
		listenerContainer.start();
	}

	@AfterAll
	static void tearDown() {
		listenerContainer.stop();
		connectionFactory.destroy();
	}

	@Test
	void sequencesAreMonotonicPerRunAndReadableWithoutIncrement() {
		long runId = System.nanoTime();

		assertThat(stream.currentSequence(runId)).isZero();
		assertThat(stream.nextSequence(runId)).isEqualTo(1L);
		assertThat(stream.nextSequence(runId)).isEqualTo(2L);
		assertThat(stream.currentSequence(runId)).isEqualTo(2L);
		// 다른 회차의 발급이 이 회차에 틈을 만들지 않는다.
		assertThat(stream.nextSequence(runId + 1)).isEqualTo(1L);
		assertThat(stream.currentSequence(runId)).isEqualTo(2L);
	}

	@Test
	void publishedEventsComeBackThroughTheSubscription() throws Exception {
		long runId = System.nanoTime();
		List<BossRunHpStreamEvent> received = new CopyOnWriteArrayList<>();
		CountDownLatch latch = new CountDownLatch(1);
		stream.addListener(event -> {
			if (event.bossRunId() == runId) {
				received.add(event);
				latch.countDown();
			}
		});
		BossRunHpStreamEvent sent = new BossRunHpStreamEvent(runId, 1L, 2, 910L, 1_000L, 90L,
				BigDecimal.valueOf(84), "ACTIVE");

		stream.publish(sent);

		assertThat(latch.await(10, TimeUnit.SECONDS))
				.as("pub/sub 왕복이 10초 안에 도착해야 한다").isTrue();
		// JSON 직렬화-역직렬화를 거친 뒤에도 모든 필드가 보존되어야 한다.
		assertThat(received).containsExactly(sent);
	}

	@Test
	void leaderboardEventsComeBackOnTheirOwnChannelWithoutCrossingIntoHpListeners() throws Exception {
		long runId = System.nanoTime();
		List<BossRunLeaderboardStreamEvent> received = new CopyOnWriteArrayList<>();
		List<BossRunHpStreamEvent> hpReceived = new CopyOnWriteArrayList<>();
		CountDownLatch latch = new CountDownLatch(1);
		stream.addLeaderboardListener(event -> {
			if (event.bossRunId() == runId) {
				received.add(event);
				latch.countDown();
			}
		});
		stream.addListener(event -> {
			if (event.bossRunId() == runId) {
				hpReceived.add(event);
			}
		});
		BossRunLeaderboardStreamEvent sent = new BossRunLeaderboardStreamEvent(runId,
				List.of(new BossRunLeaderboardStreamEvent.Entry(7L, "글쓰는너구리", 1_240L, 1L)));

		stream.publishLeaderboard(sent);

		assertThat(latch.await(10, TimeUnit.SECONDS))
				.as("pub/sub 왕복이 10초 안에 도착해야 한다").isTrue();
		assertThat(received).containsExactly(sent);
		// 두 채널이 리스너 하나로 들어오므로, 채널 구분이 깨지면 순위 이벤트가 hp 역직렬화를 오염시킨다.
		assertThat(hpReceived).isEmpty();
	}
}
