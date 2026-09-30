package com.ssafy.c206.backend.boss.api;

import com.ssafy.c206.backend.common.api.PageResponse;
import com.ssafy.c206.backend.common.security.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import com.ssafy.c206.backend.boss.domain.BossStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;

@Tag(name = "관리자 - 보스 원형", description = "보스, 분위기, 재사용 가능한 페이즈 템플릿을 관리합니다.")
public interface BossAdminApi {

	@Operation(summary = "보스 원형과 페이즈 템플릿 생성")
	@SecurityRequirement(name = "sessionAuth")
	@SecurityRequirement(name = "csrfToken")
	@ApiResponses({
			@ApiResponse(responseCode = "201", description = "보스 원형 생성 완료"),
			@ApiResponse(responseCode = "400", ref = "#/components/responses/BadRequest"),
			@ApiResponse(responseCode = "401", ref = "#/components/responses/Unauthorized"),
			@ApiResponse(responseCode = "403", ref = "#/components/responses/Forbidden")
	})
	AdminBossResponse create(
			@Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser principal,
			@RequestBody(description = "보스 정보, 분위기, 업로드된 이미지가 포함된 페이즈 목록", required = true)
			@Valid AdminBossCreateRequest request);

	@Operation(summary = "보스의 기본 책 표지 교체")
	@SecurityRequirement(name = "sessionAuth")
	@SecurityRequirement(name = "csrfToken")
	AdminBossResponse changeBookCover(
			@Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser principal,
			@Parameter(description = "보스 ID") Long bossId,
			@Valid AdminBossBookCoverUpdateRequest request);

	@Operation(summary = "보스 정보와 분위기 수정")
	@SecurityRequirement(name = "sessionAuth")
	@SecurityRequirement(name = "csrfToken")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "보스 정보 수정 완료"),
			@ApiResponse(responseCode = "400", ref = "#/components/responses/BadRequest"),
			@ApiResponse(responseCode = "403", ref = "#/components/responses/Forbidden"),
			@ApiResponse(responseCode = "404", ref = "#/components/responses/NotFound"),
			@ApiResponse(responseCode = "409", ref = "#/components/responses/Conflict")
	})
	AdminBossResponse update(
			@Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser principal,
			Long bossId, @Valid AdminBossUpdateRequest request);

	@Operation(summary = "보스 페이즈 템플릿 전체 교체")
	@SecurityRequirement(name = "sessionAuth")
	@SecurityRequirement(name = "csrfToken")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "페이즈 템플릿 교체 완료"),
			@ApiResponse(responseCode = "400", ref = "#/components/responses/BadRequest"),
			@ApiResponse(responseCode = "403", ref = "#/components/responses/Forbidden"),
			@ApiResponse(responseCode = "404", ref = "#/components/responses/NotFound"),
			@ApiResponse(responseCode = "409", ref = "#/components/responses/Conflict")
	})
	AdminBossResponse updatePhaseTemplates(
			@Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser principal,
			Long bossId, @Valid AdminBossPhaseTemplatesUpdateRequest request);

	@Operation(summary = "보스 활성 상태 변경")
	@SecurityRequirement(name = "sessionAuth")
	@SecurityRequirement(name = "csrfToken")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "보스 상태 변경 완료"),
			@ApiResponse(responseCode = "400", ref = "#/components/responses/BadRequest"),
			@ApiResponse(responseCode = "403", ref = "#/components/responses/Forbidden"),
			@ApiResponse(responseCode = "404", ref = "#/components/responses/NotFound"),
			@ApiResponse(responseCode = "409", ref = "#/components/responses/Conflict")
	})
	AdminBossResponse updateStatus(
			@Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser principal,
			Long bossId, @Valid AdminBossStatusUpdateRequest request);

	@Operation(summary = "보스 원형 목록 조회")
	@SecurityRequirement(name = "sessionAuth")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "보스 원형 페이지"),
			@ApiResponse(responseCode = "400", ref = "#/components/responses/BadRequest"),
			@ApiResponse(responseCode = "401", ref = "#/components/responses/Unauthorized"),
			@ApiResponse(responseCode = "403", ref = "#/components/responses/Forbidden")
	})
	PageResponse<AdminBossResponse> getBosses(
			String query,
			BossStatus status,
			@Parameter(description = "0부터 시작하는 페이지 번호입니다.", example = "0") @Min(0) int page,
			@Parameter(description = "페이지 크기입니다. 기본값은 20이고 최대 100입니다.", example = "20") @Min(1) @Max(100) int size);

	@Operation(summary = "보스 페이즈 템플릿 조회")
	@SecurityRequirement(name = "sessionAuth")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "보스 페이즈 템플릿"),
			@ApiResponse(responseCode = "401", ref = "#/components/responses/Unauthorized"),
			@ApiResponse(responseCode = "403", ref = "#/components/responses/Forbidden"),
			@ApiResponse(responseCode = "404", ref = "#/components/responses/NotFound")
	})
	AdminBossPhaseTemplatesResponse getPhaseTemplates(
			@Parameter(description = "보스 ID") Long bossId);
}
