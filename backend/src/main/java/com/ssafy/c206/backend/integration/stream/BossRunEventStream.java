package com.ssafy.c206.backend.integration.stream;

import java.util.function.Consumer;

/**
 * 보스 회차 이벤트의 팬아웃 채널.
 *
 * <p>현재 배포는 백엔드 컨테이너 1개라 인프로세스 전달로도 충분하지만, 발행자와 구독자(SSE
 * emitter 보유 인스턴스)가 다른 프로세스에 있을 수 있는 구조(다중 인스턴스·스케줄 작업)를
 * 대비해 처음부터 이 채널을 거친다. 배포 환경은 Redis pub/sub, 테스트·로컬 기본은 인프로세스다.
 *
 * <p>시퀀스 발급을 채널이 함께 담당하는 이유: 시퀀스는 "회차 단위로 단조 증가"가 다중 인스턴스
 * 에서도 성립해야 하므로 팬아웃과 같은 저장소(Redis)를 공유해야 한다.
 */
public interface BossRunEventStream {

	/** 회차 단위로 단조 증가하는 다음 시퀀스를 발급한다. 구독자는 이 값의 틈으로 유실을 감지한다. */
	long nextSequence(long bossRunId);

	/** 발급된 마지막 시퀀스. 아직 이벤트가 없으면 0이다. 구독 시작 스냅숏의 기준점으로 쓴다. */
	long currentSequence(long bossRunId);

	/** 모든 구독 인스턴스에 이벤트를 전달한다. 전달 실패가 발행자를 실패시키지 않아야 한다. */
	void publish(BossRunHpStreamEvent event);

	/** 이 인스턴스로 전달되는 모든 회차의 이벤트를 받는 리스너를 등록한다. */
	void addListener(Consumer<BossRunHpStreamEvent> listener);

	/**
	 * 기여도 순위 스냅숏을 모든 구독 인스턴스에 전달한다. HP 이벤트와 같은 SSE 연결로 나가지만
	 * 시퀀스를 소모하지 않는다 — 시퀀스는 HP 유실 감지 전용이고, 순위는 다음 스냅숏이 전체를
	 * 대체하므로 유실을 감지할 필요가 없다.
	 */
	void publishLeaderboard(BossRunLeaderboardStreamEvent event);

	/** 이 인스턴스로 전달되는 순위 스냅숏을 받는 리스너를 등록한다. */
	void addLeaderboardListener(Consumer<BossRunLeaderboardStreamEvent> listener);
}
