package com.ssafy.c206.backend.boss.stream.service;

import com.ssafy.c206.backend.boss.submission.service.BossSubmissionAppliedEvent;
import com.ssafy.c206.backend.integration.stream.BossRunEventStream;
import com.ssafy.c206.backend.integration.stream.BossRunHpStreamEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 커밋이 확정된 제출만 팬아웃 채널로 내보낸다.
 *
 * <p>AFTER_COMMIT이어야 하는 이유: 커밋 전에 내보내면 롤백된 데미지가 구독자 화면에 노출되고,
 * 그 값은 어떤 재동기화로도 되돌릴 수 없다(조회 API에는 존재하지 않는 상태이므로).
 *
 * <p>시퀀스도 이 시점에 발급한다. 트랜잭션 안에서 발급하면 롤백된 제출이 시퀀스를 소모해
 * 구독자가 유실로 오인하는 틈이 생긴다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BossRunStreamRelay {

	private final BossRunEventStream eventStream;

	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	public void onSubmissionApplied(BossSubmissionAppliedEvent event) {
		// AFTER_COMMIT 리스너의 예외는 호출자에게 전파되어, 데미지가 이미 커밋된 제출이 500으로
		// 보인다. 사용자는 실패로 알고 재제출하는데 HP는 깎여 있는 최악의 실패 모드다. 전파 실패는
		// 여기서 끝내고, 구독자는 다음 이벤트의 시퀀스 틈으로 유실을 감지해 재동기화한다.
		try {
			long sequence = eventStream.nextSequence(event.bossRunId());
			eventStream.publish(new BossRunHpStreamEvent(event.bossRunId(), sequence, event.phaseNo(),
					event.remainingHp(), event.maxHp(), event.accumulatedDamage(), event.appliedDamage(),
					event.baseScore(), event.runStatus().name()));
		} catch (RuntimeException exception) {
			log.error("boss_stream_relay_failed boss_run_id={} phase_no={}",
					event.bossRunId(), event.phaseNo(), exception);
		}
	}
}
