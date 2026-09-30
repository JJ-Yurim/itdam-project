package com.ssafy.c206.backend.integration.stream;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

/**
 * Redis pub/sub 기반 팬아웃. 배포 환경(develop·prod)에서 사용한다.
 *
 * <p>시퀀스는 회차별 INCR 키로 발급해 다중 인스턴스에서도 단조 증가한다. 키에는 TTL을 두어
 * 종료된 회차의 카운터가 무한히 남지 않게 한다. 회차 수명(최대 수 주) 안에서는 매 발급이 TTL을
 * 갱신하므로 진행 중 회차의 시퀀스가 사라질 일은 없다.
 */
@Slf4j
public class RedisBossRunEventStream implements BossRunEventStream, MessageListener {

	static final String CHANNEL = "boss:run:hp-events";
	static final String LEADERBOARD_CHANNEL = "boss:run:leaderboard-events";
	private static final String SEQUENCE_KEY_PREFIX = "boss:run:seq:";
	private static final Duration SEQUENCE_TTL = Duration.ofDays(60);

	// INCR과 PEXPIRE를 별개 왕복으로 보내면 그 사이 프로세스가 죽었을 때 TTL 없는 키가 남는다.
	// 매 발급마다 TTL을 함께 갱신하므로 회차가 TTL보다 오래 진행되어도 카운터가 사라지지 않는다.
	private static final RedisScript<Long> INCREMENT_WITH_TTL = RedisScript.of(
			"local seq = redis.call('INCR', KEYS[1]) "
					+ "redis.call('PEXPIRE', KEYS[1], ARGV[1]) "
					+ "return seq", Long.class);

	private final StringRedisTemplate redisTemplate;
	private final ObjectMapper objectMapper;
	private final List<Consumer<BossRunHpStreamEvent>> listeners = new CopyOnWriteArrayList<>();
	private final List<Consumer<BossRunLeaderboardStreamEvent>> leaderboardListeners = new CopyOnWriteArrayList<>();

	public RedisBossRunEventStream(StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
		this.redisTemplate = redisTemplate;
		this.objectMapper = objectMapper;
	}

	@Override
	public long nextSequence(long bossRunId) {
		String key = SEQUENCE_KEY_PREFIX + bossRunId;
		Long sequence = redisTemplate.execute(INCREMENT_WITH_TTL, List.of(key),
				String.valueOf(SEQUENCE_TTL.toMillis()));
		if (sequence == null) {
			throw new IllegalStateException("Redis INCR returned null for " + key);
		}
		return sequence;
	}

	@Override
	public long currentSequence(long bossRunId) {
		String value = redisTemplate.opsForValue().get(SEQUENCE_KEY_PREFIX + bossRunId);
		return value == null ? 0L : Long.parseLong(value);
	}

	@Override
	public void publish(BossRunHpStreamEvent event) {
		// 현실적인 실패는 직렬화가 아니라 Redis 연결 단절(런타임 예외)이다. 인터페이스 계약대로
		// 전달 실패가 발행자(제출 흐름)를 실패시키지 않도록 둘 다 여기서 끝낸다. 구독자는 다음
		// 이벤트의 시퀀스 틈으로 유실을 감지해 재동기화한다.
		try {
			redisTemplate.convertAndSend(CHANNEL, objectMapper.writeValueAsString(event));
		} catch (JsonProcessingException exception) {
			log.error("boss_stream_serialize_failed boss_run_id={} sequence={}",
					event.bossRunId(), event.sequence(), exception);
		} catch (RuntimeException exception) {
			log.warn("boss_stream_publish_failed boss_run_id={} sequence={}",
					event.bossRunId(), event.sequence(), exception);
		}
	}

	@Override
	public void addListener(Consumer<BossRunHpStreamEvent> listener) {
		listeners.add(listener);
	}

	@Override
	public void publishLeaderboard(BossRunLeaderboardStreamEvent event) {
		try {
			redisTemplate.convertAndSend(LEADERBOARD_CHANNEL, objectMapper.writeValueAsString(event));
		} catch (JsonProcessingException exception) {
			log.error("boss_stream_leaderboard_serialize_failed boss_run_id={}", event.bossRunId(), exception);
		} catch (RuntimeException exception) {
			log.warn("boss_stream_leaderboard_publish_failed boss_run_id={}", event.bossRunId(), exception);
		}
	}

	@Override
	public void addLeaderboardListener(Consumer<BossRunLeaderboardStreamEvent> listener) {
		leaderboardListeners.add(listener);
	}

	@Override
	public void onMessage(Message message, byte[] pattern) {
		// 두 채널이 같은 리스너로 들어오므로 채널 이름으로 이벤트 종류를 구분한다.
		String channel = new String(message.getChannel(), StandardCharsets.UTF_8);
		String body = new String(message.getBody(), StandardCharsets.UTF_8);
		if (LEADERBOARD_CHANNEL.equals(channel)) {
			dispatchLeaderboard(body);
			return;
		}
		dispatchHp(body);
	}

	private void dispatchHp(String body) {
		BossRunHpStreamEvent event;
		try {
			event = objectMapper.readValue(body, BossRunHpStreamEvent.class);
		} catch (JsonProcessingException exception) {
			log.warn("boss_stream_deserialize_failed", exception);
			return;
		}
		for (Consumer<BossRunHpStreamEvent> listener : listeners) {
			try {
				listener.accept(event);
			} catch (RuntimeException exception) {
				log.warn("boss_stream_listener_failed boss_run_id={} sequence={}",
						event.bossRunId(), event.sequence(), exception);
			}
		}
	}

	private void dispatchLeaderboard(String body) {
		BossRunLeaderboardStreamEvent event;
		try {
			event = objectMapper.readValue(body, BossRunLeaderboardStreamEvent.class);
		} catch (JsonProcessingException exception) {
			log.warn("boss_stream_leaderboard_deserialize_failed", exception);
			return;
		}
		for (Consumer<BossRunLeaderboardStreamEvent> listener : leaderboardListeners) {
			try {
				listener.accept(event);
			} catch (RuntimeException exception) {
				log.warn("boss_stream_leaderboard_listener_failed boss_run_id={}", event.bossRunId(), exception);
			}
		}
	}
}
