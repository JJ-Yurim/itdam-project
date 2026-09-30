package com.ssafy.c206.backend.boss.stream.service;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import com.ssafy.c206.backend.boss.stream.api.BossRunLeaderboardEventPayload;
import com.ssafy.c206.backend.boss.stream.api.BossRunStreamEventPayload;
import com.ssafy.c206.backend.integration.stream.BossRunEventStream;
import com.ssafy.c206.backend.integration.stream.BossRunHpStreamEvent;
import com.ssafy.c206.backend.integration.stream.BossRunLeaderboardStreamEvent;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 이 인스턴스에 붙어 있는 SSE 연결의 수명을 관리한다.
 *
 * <p>emitter는 연결 종료·타임아웃·전송 실패·회차 종료 네 경로 모두에서 맵에서 제거되어야 한다.
 * 어느 하나라도 빠지면 죽은 연결이 쌓여 하트비트와 브로드캐스트가 계속 그쪽으로 시도된다.
 *
 * <p>모든 전송(브로드캐스트·하트비트)은 전용 단일 스레드에서 실행한다.
 * 인프로세스 채널에서는 브로드캐스트가 제출 요청 스레드에서 시작되므로, 그대로 두면 느린 구독자
 * 하나가 제출자의 응답 시간을 붙잡는다. 스레드가 하나라 회차 안의 이벤트 순서도 그대로 유지되고,
 * 공용 스케줄러(기본 풀 1)의 다른 작업과도 간섭하지 않는다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BossRunStreamRegistry {

	/** EventSource가 자동 재연결하므로 무한 대신 유한 타임아웃을 두고 주기적으로 끊는다. */
	static final long EMITTER_TIMEOUT_MILLIS = 30L * 60 * 1000;
	private static final long HEARTBEAT_INTERVAL_MILLIS = 25_000;

	private final BossRunEventStream eventStream;
	private final Map<Long, Set<SseEmitter>> emittersByRunId = new ConcurrentHashMap<>();
	/**
	 * 종료 이벤트를 이미 브로드캐스트한 회차. 구독 검증(ACTIVE 확인)과 등록 사이에 회차가 끝나는
	 * 경합에서, 종료를 놓친 새 연결이 타임아웃까지 하트비트만 소비하는 것을 막는다.
	 * 종료된 회차는 되돌아오지 않고 개수도 회차 수만큼이라 무한히 커지지 않는다.
	 */
	private final Set<Long> terminatedRunIds = ConcurrentHashMap.newKeySet();
	private final ScheduledExecutorService streamExecutor = Executors.newSingleThreadScheduledExecutor(runnable -> {
		Thread thread = new Thread(runnable, "boss-stream-sender");
		thread.setDaemon(true);
		return thread;
	});

	@PostConstruct
	void start() {
		eventStream.addListener(event -> streamExecutor.execute(() -> broadcast(event)));
		eventStream.addLeaderboardListener(event -> streamExecutor.execute(() -> broadcastLeaderboard(event)));
		streamExecutor.scheduleWithFixedDelay(this::heartbeat,
				HEARTBEAT_INTERVAL_MILLIS, HEARTBEAT_INTERVAL_MILLIS, TimeUnit.MILLISECONDS);
	}

	@PreDestroy
	void stop() {
		streamExecutor.shutdownNow();
	}

	/** 타임아웃만 설정된 emitter를 만든다. 스냅숏 전송이 성공한 뒤 {@link #register}로 등록한다. */
	public SseEmitter createEmitter() {
		return new SseEmitter(EMITTER_TIMEOUT_MILLIS);
	}

	/** 연결을 브로드캐스트 대상에 넣고, 어떤 경로로 끝나든 맵에서 빠지도록 콜백을 건다. */
	public void register(long bossRunId, SseEmitter emitter) {
		Set<SseEmitter> emitters = emittersByRunId.computeIfAbsent(bossRunId, key -> ConcurrentHashMap.newKeySet());
		emitters.add(emitter);
		emitter.onCompletion(() -> remove(bossRunId, emitter));
		emitter.onTimeout(emitter::complete);
		emitter.onError(throwable -> remove(bossRunId, emitter));
		// 구독 검증과 등록 사이에 회차가 종료되어 종료 브로드캐스트를 놓친 연결은 즉시 닫는다.
		// 등록 후에 확인해야 한다 — 확인을 먼저 하면 그 직후 종료가 끼어드는 같은 경합이 남는다.
		if (terminatedRunIds.contains(bossRunId)) {
			emitter.complete();
		}
	}

	private void broadcast(BossRunHpStreamEvent event) {
		Set<SseEmitter> emitters = emittersByRunId.get(event.bossRunId());
		if (emitters != null) {
			BossRunStreamEventPayload payload = BossRunStreamEventPayload.from(event);
			for (SseEmitter emitter : List.copyOf(emitters)) {
				send(event.bossRunId(), emitter, event.sequence(), payload);
			}
		}
		// 회차가 끝났으면 남은 연결을 이쪽에서 닫는다. 클라이언트가 재연결해도 구독 검증에서
		// 거절되므로, 열어 두면 하트비트만 소비하는 죽은 채널이 된다.
		if (isTerminal(event.runStatus())) {
			terminatedRunIds.add(event.bossRunId());
			completeAll(event.bossRunId());
		}
	}

	private void broadcastLeaderboard(BossRunLeaderboardStreamEvent event) {
		Set<SseEmitter> emitters = emittersByRunId.get(event.bossRunId());
		if (emitters == null) {
			return;
		}
		BossRunLeaderboardEventPayload payload = BossRunLeaderboardEventPayload.from(event);
		for (SseEmitter emitter : List.copyOf(emitters)) {
			try {
				// id를 싣지 않는다 — 시퀀스는 hp 유실 감지 전용이라, 여기서 소모하면 클라이언트가
				// hp 시퀀스 틈을 유실로 오인해 불필요한 재동기화를 반복한다.
				emitter.send(SseEmitter.event().name("leaderboard").data(payload, MediaType.APPLICATION_JSON));
			} catch (IOException | RuntimeException exception) {
				remove(event.bossRunId(), emitter);
			}
		}
	}

	private void send(long bossRunId, SseEmitter emitter, long sequence, BossRunStreamEventPayload payload) {
		try {
			emitter.send(SseEmitter.event().id(String.valueOf(sequence)).name("hp")
					.data(payload, MediaType.APPLICATION_JSON));
		} catch (IOException | RuntimeException exception) {
			// 끊긴 연결로의 전송 실패는 정상 경로다. 제거만 하고 다른 구독자 전송을 계속한다.
			remove(bossRunId, emitter);
		}
	}

	/** 유휴 연결이 중간 프록시(호스트 nginx 등)에서 끊기지 않도록 주석 이벤트를 주기적으로 보낸다. */
	private void heartbeat() {
		emittersByRunId.forEach((bossRunId, emitters) -> {
			for (SseEmitter emitter : List.copyOf(emitters)) {
				try {
					emitter.send(SseEmitter.event().comment("heartbeat"));
				} catch (IOException | RuntimeException exception) {
					remove(bossRunId, emitter);
				}
			}
		});
	}

	private void completeAll(long bossRunId) {
		Set<SseEmitter> emitters = emittersByRunId.remove(bossRunId);
		if (emitters == null) {
			return;
		}
		for (SseEmitter emitter : emitters) {
			try {
				emitter.complete();
			} catch (RuntimeException exception) {
				log.debug("boss_stream_complete_failed boss_run_id={}", bossRunId, exception);
			}
		}
	}

	private void remove(long bossRunId, SseEmitter emitter) {
		Set<SseEmitter> emitters = emittersByRunId.get(bossRunId);
		if (emitters == null) {
			return;
		}
		emitters.remove(emitter);
		if (emitters.isEmpty()) {
			// 값이 비었을 때만 키를 지운다. remove(key)를 무조건 하면 그 사이 등록된 새 연결이 사라진다.
			emittersByRunId.remove(bossRunId, emitters);
		}
	}

	private static boolean isTerminal(String runStatus) {
		return !"ACTIVE".equals(runStatus);
	}

	/** 테스트가 emitter 누수를 검증할 때 쓴다. */
	int connectionCount(long bossRunId) {
		Set<SseEmitter> emitters = emittersByRunId.get(bossRunId);
		return emitters == null ? 0 : emitters.size();
	}
}
