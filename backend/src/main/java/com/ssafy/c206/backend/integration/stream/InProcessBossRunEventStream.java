package com.ssafy.c206.backend.integration.stream;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 단일 인스턴스용 팬아웃. Redis 없이 기동해야 하는 테스트·로컬 환경의 기본 구현이다.
 *
 * <p>시퀀스가 프로세스 메모리에 있으므로 재시작하면 0부터 다시 시작한다. 구독자는 시퀀스가
 * 줄어든 것을 유실과 동일하게 재동기화로 처리하므로 동작은 유지된다.
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "boss.stream", name = "channel", havingValue = "in-process", matchIfMissing = true)
public class InProcessBossRunEventStream implements BossRunEventStream {

	private final Map<Long, AtomicLong> sequences = new ConcurrentHashMap<>();
	private final List<Consumer<BossRunHpStreamEvent>> listeners = new CopyOnWriteArrayList<>();
	private final List<Consumer<BossRunLeaderboardStreamEvent>> leaderboardListeners = new CopyOnWriteArrayList<>();

	@Override
	public long nextSequence(long bossRunId) {
		return sequences.computeIfAbsent(bossRunId, key -> new AtomicLong()).incrementAndGet();
	}

	@Override
	public long currentSequence(long bossRunId) {
		AtomicLong sequence = sequences.get(bossRunId);
		return sequence == null ? 0L : sequence.get();
	}

	@Override
	public void publish(BossRunHpStreamEvent event) {
		for (Consumer<BossRunHpStreamEvent> listener : listeners) {
			try {
				listener.accept(event);
			} catch (RuntimeException exception) {
				// 구독자 하나의 실패가 발행(제출 흐름)과 다른 구독자를 막지 않는다.
				log.warn("boss_stream_listener_failed boss_run_id={} sequence={}",
						event.bossRunId(), event.sequence(), exception);
			}
		}
	}

	@Override
	public void addListener(Consumer<BossRunHpStreamEvent> listener) {
		listeners.add(listener);
	}

	@Override
	public void publishLeaderboard(BossRunLeaderboardStreamEvent event) {
		for (Consumer<BossRunLeaderboardStreamEvent> listener : leaderboardListeners) {
			try {
				listener.accept(event);
			} catch (RuntimeException exception) {
				log.warn("boss_stream_leaderboard_listener_failed boss_run_id={}", event.bossRunId(), exception);
			}
		}
	}

	@Override
	public void addLeaderboardListener(Consumer<BossRunLeaderboardStreamEvent> listener) {
		leaderboardListeners.add(listener);
	}
}
