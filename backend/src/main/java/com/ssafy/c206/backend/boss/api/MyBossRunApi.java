package com.ssafy.c206.backend.boss.api;

import com.ssafy.c206.backend.boss.domain.BossRunStatus;
import com.ssafy.c206.backend.common.api.PageResponse;
import com.ssafy.c206.backend.common.security.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

@Tag(name = "내 보스전 이력", description = "로그인 사용자가 문장을 제출해 참여한 보스런을 조회합니다.")
public interface MyBossRunApi {

	@Operation(
			summary = "내가 참여한 보스런 목록 조회",
			description = "현재 사용자가 한 번 이상 문장을 제출한 보스런을 최근 제출 시각 역순으로 페이지 조회합니다. "
					+ "같은 보스런에 여러 번 제출해도 한 번만 반환하며, status를 생략하면 모든 상태를 조회합니다.")
	@SecurityRequirement(name = "sessionAuth")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "내 참여 보스런 페이지"),
			@ApiResponse(responseCode = "400", ref = "#/components/responses/BadRequest"),
			@ApiResponse(responseCode = "401", ref = "#/components/responses/Unauthorized"),
			@ApiResponse(responseCode = "403", ref = "#/components/responses/Forbidden")
	})
	PageResponse<MyBossRunResponse> getMyBossRuns(
			@Parameter(hidden = true) AuthenticatedUser principal,
			@Parameter(description = "보스런 상태 필터입니다. 생략하면 모든 상태를 조회합니다.", example = "CLEARED")
			BossRunStatus status,
			@Parameter(description = "0부터 시작하는 페이지 번호입니다.", example = "0") @Min(0) int page,
			@Parameter(description = "페이지 크기입니다. 기본값은 20이고 최대 100입니다.", example = "20") @Min(1) @Max(100) int size);
}
