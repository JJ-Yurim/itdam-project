package com.ssafy.c206.backend.integration.stream;

import java.util.List;

/**
 * 회차 기여도 순위 스냅숏을 구독자에게 전달하는 전송 DTO.
 *
 * <p>HP 이벤트와 달리 시퀀스가 없다. 매 제출이 아니라 병합 주기(약 1초)마다 최신 스냅숏이
 * 통째로 전송되고, 하나를 놓쳐도 다음 스냅숏이 전체를 대체하므로 유실 감지가 필요 없다.
 */
public record BossRunLeaderboardStreamEvent(long bossRunId, List<Entry> entries) {

	public record Entry(long userId, String nickname, long totalDamage, long rank) {
	}
}
