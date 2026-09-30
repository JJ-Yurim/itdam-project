package com.ssafy.c206.backend.boss.stream.api;

import java.util.List;

import com.ssafy.c206.backend.integration.stream.BossRunLeaderboardStreamEvent;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * SSE `leaderboard` 이벤트의 data 본문. 항상 최신 스냅숏 전체이므로 클라이언트는 이전 상태와
 * 병합하지 않고 그대로 대체한다.
 *
 * <p>hp 이벤트와 달리 SSE id를 싣지 않는다. id(시퀀스)는 hp 유실 감지 전용이라, 여기서 소모하면
 * 클라이언트가 hp 시퀀스 틈을 유실로 오인한다.
 */
public record BossRunLeaderboardEventPayload(
		@Schema(description = "보스 회차 식별자입니다.", example = "12") long bossRunId,
		@Schema(description = "누적 데미지 상위 참가자 목록입니다.") List<Entry> entries) {

	public record Entry(
			@Schema(description = "사용자 식별자입니다.", example = "7") long userId,
			@Schema(description = "사용자 닉네임입니다.", example = "글쓰는너구리") String nickname,
			@Schema(description = "이 회차의 누적 데미지입니다.", example = "1240") long totalDamage,
			@Schema(description = "순위입니다. 1부터 시작합니다.", example = "1") long rank) {
	}

	public static BossRunLeaderboardEventPayload from(BossRunLeaderboardStreamEvent event) {
		return new BossRunLeaderboardEventPayload(event.bossRunId(), event.entries().stream()
				.map(entry -> new Entry(entry.userId(), entry.nickname(), entry.totalDamage(), entry.rank()))
				.toList());
	}
}
