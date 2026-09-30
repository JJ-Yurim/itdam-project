package com.ssafy.c206.backend.boss.leaderboard.api;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

public record BossRunLeaderboardResponse(
		@Schema(description = "보스 회차 식별자입니다.", example = "12") long bossRunId,
		@Schema(description = "누적 데미지 상위 참가자 목록입니다.") List<BossRunLeaderboardEntryResponse> entries,
		@Schema(description = "요청자의 순위입니다. 아직 제출하지 않았으면 null입니다.") MyRank me) {

	public record MyRank(
			@Schema(description = "요청자의 순위입니다. 1부터 시작합니다.", example = "23") long rank,
			@Schema(description = "요청자의 누적 데미지입니다.", example = "310") long totalDamage) {
	}
}
