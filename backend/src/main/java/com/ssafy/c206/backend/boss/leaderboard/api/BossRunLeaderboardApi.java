package com.ssafy.c206.backend.boss.leaderboard.api;

import com.ssafy.c206.backend.common.security.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "보스전 리더보드", description = "회차별 기여도(누적 데미지) 순위를 조회합니다.")
public interface BossRunLeaderboardApi {

	@Operation(
			summary = "보스전 기여도 리더보드 조회",
			description = "회차의 누적 데미지 상위 10명과 요청자 자신의 순위를 반환합니다. "
					+ "진행 중 회차는 SSE 스트림(GET /api/v1/boss-runs/{bossRunId}/events)의 leaderboard "
					+ "이벤트로 약 1초 주기의 갱신을 함께 받을 수 있습니다. leaderboard 이벤트는 항상 최신 "
					+ "스냅숏 전체이므로 이 응답을 그대로 대체하면 됩니다. "
					+ "아직 아무도 제출하지 않은 회차는 빈 목록이 반환됩니다.")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "리더보드 조회 성공"),
			@ApiResponse(responseCode = "401", ref = "#/components/responses/Unauthorized"),
			@ApiResponse(responseCode = "404", ref = "#/components/responses/NotFound")
	})
	@SecurityRequirement(name = "sessionCookie")
	BossRunLeaderboardResponse getLeaderboard(
			@Parameter(hidden = true) AuthenticatedUser principal,
			@Parameter(description = "조회할 보스전 회차 식별자입니다.", example = "12") Long bossRunId);
}
