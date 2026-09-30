package com.ssafy.c206.backend.boss.leaderboard.api;

import io.swagger.v3.oas.annotations.media.Schema;

public record BossRunLeaderboardEntryResponse(
		@Schema(description = "순위입니다. 누적 데미지 내림차순 정렬 위치 기준 1부터 시작합니다.", example = "1") long rank,
		@Schema(description = "사용자 식별자입니다.", example = "7") long userId,
		@Schema(description = "사용자 닉네임입니다.", example = "글쓰는너구리") String nickname,
		@Schema(description = "이 회차의 누적 데미지입니다.", example = "1240") long totalDamage) {
}
