package com.ssafy.c206.backend.boss.api;

import java.time.Instant;
import java.util.List;

import com.ssafy.c206.backend.boss.domain.BossRunStatus;
import com.ssafy.c206.backend.common.api.PageResponse;
import com.ssafy.c206.backend.common.security.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import jakarta.validation.Valid;

@Tag(
		name = "관리자 - 보스런 회차",
		description = "ADMIN 역할 세션 전용 운영 API입니다. 페이즈 이미지 스냅샷을 포함한 보스런 회차를 관리합니다.")
public interface BossRunAdminApi {

	@Operation(
			summary = "보스런 생성과 기본 템플릿 갱신",
			description = "대상 보스와 기간, 최종 페이즈 구성으로 회차를 만들고 다음 생성에 사용할 기본 템플릿을 갱신합니다. "
					+ "activate가 true일 때 같은 보스에 ACTIVE 회차가 이미 있으면 409를 반환합니다.")
	@SecurityRequirement(name = "sessionAuth")
	@SecurityRequirement(name = "csrfToken")
	@ApiResponses({
			@ApiResponse(responseCode = "201", description = "보스런 회차 생성 완료"),
			@ApiResponse(responseCode = "400", ref = "#/components/responses/BadRequest"),
			@ApiResponse(responseCode = "401", ref = "#/components/responses/Unauthorized"),
			@ApiResponse(responseCode = "403", ref = "#/components/responses/Forbidden"),
			@ApiResponse(responseCode = "404", ref = "#/components/responses/NotFound"),
			@ApiResponse(responseCode = "409", ref = "#/components/responses/Conflict")
	})
	AdminBossRunResponse create(
			@Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser principal,
			@RequestBody(description = "기간과 최종 페이즈 목록", required = true)
			AdminBossRunCreateRequest request);

	@Operation(summary = "레이드 설정 수정")
	@SecurityRequirement(name = "sessionAuth")
	@SecurityRequirement(name = "csrfToken")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "레이드 수정 완료"),
			@ApiResponse(responseCode = "400", ref = "#/components/responses/BadRequest"),
			@ApiResponse(responseCode = "401", ref = "#/components/responses/Unauthorized"),
			@ApiResponse(responseCode = "403", ref = "#/components/responses/Forbidden"),
			@ApiResponse(responseCode = "404", ref = "#/components/responses/NotFound"),
			@ApiResponse(responseCode = "409", ref = "#/components/responses/Conflict")
	})
	AdminBossRunResponse update(
			@Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser principal,
			Long runId, @Valid AdminBossRunUpdateRequest request);

	@Operation(summary = "예정 레이드 즉시 시작")
	@SecurityRequirement(name = "sessionAuth")
	@SecurityRequirement(name = "csrfToken")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "레이드 시작 완료"),
			@ApiResponse(responseCode = "400", ref = "#/components/responses/BadRequest"),
			@ApiResponse(responseCode = "401", ref = "#/components/responses/Unauthorized"),
			@ApiResponse(responseCode = "403", ref = "#/components/responses/Forbidden"),
			@ApiResponse(responseCode = "404", ref = "#/components/responses/NotFound"),
			@ApiResponse(responseCode = "409", ref = "#/components/responses/Conflict")
	})
	AdminBossRunResponse activate(
			@Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser principal,
			Long runId, @Valid AdminBossRunActionRequest request);

	@Operation(summary = "예정 레이드 취소")
	@SecurityRequirement(name = "sessionAuth")
	@SecurityRequirement(name = "csrfToken")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "레이드 취소 완료"),
			@ApiResponse(responseCode = "400", ref = "#/components/responses/BadRequest"),
			@ApiResponse(responseCode = "401", ref = "#/components/responses/Unauthorized"),
			@ApiResponse(responseCode = "403", ref = "#/components/responses/Forbidden"),
			@ApiResponse(responseCode = "404", ref = "#/components/responses/NotFound"),
			@ApiResponse(responseCode = "409", ref = "#/components/responses/Conflict")
	})
	AdminBossRunResponse cancel(
			@Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser principal,
			Long runId, @Valid AdminBossRunActionRequest request);

	@Operation(
			summary = "예정·진행 중 레이드 페이즈 이미지 변경",
			description = "SCHEDULED·ACTIVE 회차의 지정 페이즈 이미지 참조만 교체합니다. 종료 상태(CLEARED, ENDED_UNCLEARED, "
					+ "CLOSED, CANCELLED)와 이미 만료된 ACTIVE 회차는 409를 반환하고, 새 이미지는 관리자 본인이 업로드한 "
					+ "TEMPORARY 상태의 BOSS_PHASE 자산만 허용합니다.")
	@SecurityRequirement(name = "sessionAuth")
	@SecurityRequirement(name = "csrfToken")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "페이즈 이미지 변경 완료"),
			@ApiResponse(responseCode = "400", ref = "#/components/responses/BadRequest"),
			@ApiResponse(responseCode = "401", ref = "#/components/responses/Unauthorized"),
			@ApiResponse(responseCode = "403", ref = "#/components/responses/Forbidden"),
			@ApiResponse(responseCode = "404", ref = "#/components/responses/NotFound"),
			@ApiResponse(responseCode = "409", ref = "#/components/responses/Conflict")
	})
	AdminBossRunResponse changePhaseImage(
			@Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser principal,
			Long runId,
			@Parameter(description = "1부터 시작하는 페이즈 번호입니다.", example = "2") int phaseNo,
			@Valid AdminBossPhaseImageUpdateRequest request);

	@Operation(summary = "진행 중 레이드 조기 종료")
	@SecurityRequirement(name = "sessionAuth")
	@SecurityRequirement(name = "csrfToken")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "레이드 조기 종료 완료"),
			@ApiResponse(responseCode = "400", ref = "#/components/responses/BadRequest"),
			@ApiResponse(responseCode = "401", ref = "#/components/responses/Unauthorized"),
			@ApiResponse(responseCode = "403", ref = "#/components/responses/Forbidden"),
			@ApiResponse(responseCode = "404", ref = "#/components/responses/NotFound"),
			@ApiResponse(responseCode = "409", ref = "#/components/responses/Conflict")
	})
	AdminBossRunResponse end(
			@Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser principal,
			Long runId, @Valid AdminBossRunActionRequest request);

	@Operation(summary = "보스런 회차 목록 조회")
	@SecurityRequirement(name = "sessionAuth")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "보스런 회차 페이지"),
			@ApiResponse(responseCode = "400", ref = "#/components/responses/BadRequest"),
			@ApiResponse(responseCode = "401", ref = "#/components/responses/Unauthorized"),
			@ApiResponse(responseCode = "403", ref = "#/components/responses/Forbidden")
	})
	PageResponse<AdminBossRunResponse> getRuns(
			String query,
			List<BossRunStatus> statuses,
			Instant from,
			Instant to,
			@Parameter(description = "0부터 시작하는 페이지 번호입니다.", example = "0") @Min(0) int page,
			@Parameter(description = "페이지 크기입니다. 기본값은 20이고 최대 100입니다.", example = "20") @Min(1) @Max(100) int size);
}
