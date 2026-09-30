package com.ssafy.c206.backend.boss.stream.service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.time.Instant;

import com.ssafy.c206.backend.boss.domain.BossPhase;
import com.ssafy.c206.backend.boss.domain.BossRun;
import com.ssafy.c206.backend.boss.domain.BossRunStatus;
import com.ssafy.c206.backend.boss.repository.BossPhaseRepository;
import com.ssafy.c206.backend.boss.repository.BossRunRepository;
import com.ssafy.c206.backend.boss.service.BossRunLifecycleService;
import com.ssafy.c206.backend.boss.stream.api.BossRunStreamEventPayload;
import com.ssafy.c206.backend.common.api.DomainException;
import com.ssafy.c206.backend.common.api.ErrorCode;
import com.ssafy.c206.backend.common.security.AuthenticatedUser;
import com.ssafy.c206.backend.common.security.AuthenticatedUsers;
import com.ssafy.c206.backend.common.time.ServiceClock;
import com.ssafy.c206.backend.integration.stream.BossRunEventStream;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 진행 중 보스 회차의 SSE 구독을 연다.
 *
 * <p>구독 시작 시 현재 상태 스냅숏을 첫 이벤트로 보낸다. 스냅숏이 없으면 클라이언트는 첫 제출이
 * 일어날 때까지 기준 시퀀스를 모른 채로 있어, 재연결 직후의 유실을 감지할 수 없다.
 */
@Service
@RequiredArgsConstructor
public class BossRunStreamService {

	private final BossRunLifecycleService lifecycleService;
	private final BossRunRepository bossRunRepository;
	private final BossPhaseRepository bossPhaseRepository;
	private final BossRunStreamRegistry registry;
	private final BossRunEventStream eventStream;
	private final ServiceClock serviceClock;

	public SseEmitter subscribe(AuthenticatedUser principal, Long bossRunId) {
		AuthenticatedUsers.requireUserId(principal);
		Instant requestTime = serviceClock.now();
		// 조회 흐름과 같은 지연 전이를 먼저 태워, 시각상 끝난 회차의 구독이 ACTIVE로 통과하지 않게 한다.
		lifecycleService.syncStateIfNeeded(bossRunId, requestTime);
		BossRun run = bossRunRepository.findWithBossById(bossRunId)
				.orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND));
		if (run.getStatus() != BossRunStatus.ACTIVE) {
			throw new DomainException(ErrorCode.CONFLICT, "Only an active boss run can be subscribed.");
		}
		BossPhase phase = bossPhaseRepository.findCurrentByBossRunId(run.getId(), run.getCurrentPhaseNo())
				.orElseThrow(() -> new DomainException(ErrorCode.CONFLICT, "Boss run has no current phase."));

		// 스냅숏을 먼저 쓰고 나서 등록한다. 등록을 먼저 하면 그 틈에 끼어든 브로드캐스트(id N)가
		// 스냅숏(같은 id N, 반영 전 값)보다 먼저 도착해, 클라이언트가 시퀀스 틈 없이 낡은 HP를
		// 최신값으로 갖게 된다. 스냅숏이 먼저면 그 틈의 이벤트는 시퀀스 틈으로 감지되는 유실이
		// 되어 재동기화로 복구된다 — 감지 불가능한 낡은 상태보다 감지 가능한 유실이 낫다.
		// 스냅숏 전송이 실패하는 경우에도 아직 등록 전이라 레지스트리에 잔여물이 남지 않는다.
		SseEmitter emitter = registry.createEmitter();
		sendSnapshot(emitter, run, phase);
		registry.register(run.getId(), emitter);
		return emitter;
	}

	/**
	 * 현재 상태를 첫 이벤트로 싣는다. id는 지금까지 발급된 마지막 시퀀스라, 클라이언트는 이후
	 * 수신하는 이벤트의 시퀀스가 이 값에서 1씩 증가하는지로 유실을 판정한다.
	 */
	private void sendSnapshot(SseEmitter emitter, BossRun run, BossPhase phase) {
		BossRunStreamEventPayload snapshot = new BossRunStreamEventPayload(run.getId(), phase.getPhaseNo(),
				phase.getRemainingHp(), phase.getMaxHp(), run.getAccumulatedDamage(), 0L, BigDecimal.ZERO,
				run.getStatus().name());
		try {
			emitter.send(SseEmitter.event().id(String.valueOf(eventStream.currentSequence(run.getId())))
					.name("hp").data(snapshot, MediaType.APPLICATION_JSON));
		} catch (IOException exception) {
			// 응답이 열리기 전의 전송은 버퍼링되므로 여기 도달하면 연결 자체가 이미 죽은 것이다.
			throw new UncheckedIOException("Failed to send the subscription snapshot.", exception);
		}
	}
}
