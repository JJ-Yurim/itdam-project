package com.ssafy.c206.backend.sentence.archive.api;

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
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

@Tag(
		name = "내 문장 보관함",
		description = "평가 완료한 내 문장을 페이지로 조회하고 상세 결과를 확인합니다.")
public interface ArchiveApi {

	@Operation(
			summary = "내 문장 보관함 페이지 조회",
			description = "로그인한 사용자의 보관 문장을 페이지 단위로 조회합니다. 세션 쿠키로 인증하고 page와 size를 쿼리로 전달하며 본문은 없습니다. page는 0부터 시작하고 기본 size는 20, 최대 size는 100입니다. 성공 시 페이지(200)를 받고, 400은 page·size 범위를 수정, 401은 로그인, 403은 계정을 활성화한 뒤 재시도합니다.")
	@SecurityRequirement(name = "sessionAuth")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "내 문장 보관함 페이지"),
			@ApiResponse(responseCode = "400", ref = "#/components/responses/BadRequest"),
			@ApiResponse(responseCode = "401", ref = "#/components/responses/Unauthorized"),
			@ApiResponse(responseCode = "403", ref = "#/components/responses/Forbidden")
	})
	PageResponse<ArchiveSentenceResponse> getMyArchive(
			@Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser principal,
			@Parameter(description = "0부터 시작하는 페이지 번호입니다. 기본값은 0입니다.", example = "0")
			@RequestParam(defaultValue = "0") @Min(0) int page,
			@Parameter(description = "페이지당 항목 수입니다. 기본값은 20이고 최대 100입니다.", example = "20")
			@RequestParam(defaultValue = "20") @Min(1) @Max(100) int size);

	@Operation(
			summary = "내 미평가 문장 페이지 조회",
			description = "로그인한 사용자의 DRAFT, EVALUATING, EVALUATION_FAILED 문장 버전을 최신 생성순으로 조회합니다.")
	@SecurityRequirement(name = "sessionAuth")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "미평가 문장 페이지"),
			@ApiResponse(responseCode = "400", ref = "#/components/responses/BadRequest"),
			@ApiResponse(responseCode = "401", ref = "#/components/responses/Unauthorized"),
			@ApiResponse(responseCode = "403", ref = "#/components/responses/Forbidden")
	})
	PageResponse<UnevaluatedArchiveSentenceResponse> getMyUnevaluatedArchive(
			@Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser principal,
			@Parameter(description = "0부터 시작하는 페이지 번호입니다.", example = "0")
			@RequestParam(defaultValue = "0") @Min(0) int page,
			@Parameter(description = "페이지당 항목 수입니다. 최대 100입니다.", example = "20")
			@RequestParam(defaultValue = "20") @Min(1) @Max(100) int size);

	@Operation(
			summary = "내 문장 보관함 상세 조회",
			description = "보관한 문장 버전의 평가 점수, 분위기, 단어 사용 정보를 조회합니다. 세션 쿠키로 인증하고 sentenceVersionId를 경로로 전달하며 본문은 없습니다. 성공 시 상세 정보(200)를 받고, 403이면 계정을 활성화한 뒤 다시 호출하고, 404는 보관함에 있는 버전인지 확인한 뒤 재시도합니다.")
	@SecurityRequirement(name = "sessionAuth")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "내 문장 보관함 상세"),
			@ApiResponse(responseCode = "401", ref = "#/components/responses/Unauthorized"),
			@ApiResponse(responseCode = "403", ref = "#/components/responses/Forbidden"),
			@ApiResponse(responseCode = "404", ref = "#/components/responses/NotFound")
	})
	ArchiveSentenceResponse getMyArchiveDetail(
			@Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser principal,
			@Parameter(description = "조회할 보관 문장 버전 식별자입니다.", example = "84")
			@PathVariable Long sentenceVersionId);
}
