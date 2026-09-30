package com.ssafy.c206.backend.sentence.api;

import com.ssafy.c206.backend.common.security.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;

@Tag(name = "문장 관리", description = "문장을 작성하고 버전별로 관리합니다.")
public interface SentenceApi {

	@Operation(
			summary = "문장 작성",
			description = "새 문장과 첫 DRAFT 버전을 만듭니다. 세션 쿠키와 X-XSRF-TOKEN 헤더를 보내고 JSON 본문으로 content를 전달합니다.")
	@SecurityRequirement(name = "sessionAuth")
	@SecurityRequirement(name = "csrfToken")
	@ApiResponses({
			@ApiResponse(responseCode = "201", description = "문장과 첫 버전 생성 완료"),
			@ApiResponse(responseCode = "400", ref = "#/components/responses/BadRequest"),
			@ApiResponse(responseCode = "401", ref = "#/components/responses/Unauthorized"),
			@ApiResponse(responseCode = "403", ref = "#/components/responses/Forbidden")
	})
	SentenceVersionResponse create(
			@Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser principal,
			@RequestBody(
					required = true,
					description = "새 문장 내용을 담은 JSON 본문")
			@Valid SentenceRequest request);

	@Operation(
			summary = "문장 버전 추가",
			description = "기존 문장에 새 DRAFT 버전을 추가합니다. sentenceId는 경로로, content는 JSON 본문으로 전달합니다.")
	@SecurityRequirement(name = "sessionAuth")
	@SecurityRequirement(name = "csrfToken")
	@ApiResponses({
			@ApiResponse(responseCode = "201", description = "새 문장 버전 생성 완료"),
			@ApiResponse(responseCode = "400", ref = "#/components/responses/BadRequest"),
			@ApiResponse(responseCode = "401", ref = "#/components/responses/Unauthorized"),
			@ApiResponse(responseCode = "403", ref = "#/components/responses/Forbidden"),
			@ApiResponse(responseCode = "404", ref = "#/components/responses/NotFound"),
			@ApiResponse(responseCode = "409", ref = "#/components/responses/Conflict")
	})
	SentenceVersionResponse createVersion(
			@Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser principal,
			@Parameter(description = "문장 식별자", example = "42") @PathVariable Long sentenceId,
			@RequestBody(required = true)
			@Valid SentenceRequest request);

	@Operation(
			summary = "초안 문장 버전 수정",
			description = "DRAFT 상태의 문장 버전만 수정합니다. versionId는 경로로, content는 JSON 본문으로 전달합니다.")
	@SecurityRequirement(name = "sessionAuth")
	@SecurityRequirement(name = "csrfToken")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "문장 버전 수정 완료"),
			@ApiResponse(responseCode = "400", ref = "#/components/responses/BadRequest"),
			@ApiResponse(responseCode = "401", ref = "#/components/responses/Unauthorized"),
			@ApiResponse(responseCode = "403", ref = "#/components/responses/Forbidden"),
			@ApiResponse(responseCode = "404", ref = "#/components/responses/NotFound"),
			@ApiResponse(responseCode = "409", ref = "#/components/responses/Conflict")
	})
	SentenceVersionResponse updateDraft(
			@Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser principal,
			@Parameter(description = "문장 버전 식별자", example = "84") @PathVariable Long versionId,
			@RequestBody(required = true)
			@Valid SentenceRequest request);

	@Operation(
			summary = "문장 조회",
			description = "문장과 소유한 버전 목록을 조회합니다. 세션 쿠키를 보내고 sentenceId를 경로로 전달합니다.")
	@SecurityRequirement(name = "sessionAuth")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "문장과 버전 목록"),
			@ApiResponse(responseCode = "401", ref = "#/components/responses/Unauthorized"),
			@ApiResponse(responseCode = "403", ref = "#/components/responses/Forbidden"),
			@ApiResponse(responseCode = "404", ref = "#/components/responses/NotFound")
	})
	SentenceResponse get(
			@Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser principal,
			@Parameter(description = "문장 식별자", example = "42") @PathVariable Long sentenceId);

	@Operation(
			summary = "문장 버전 조회",
			description = "특정 문장 버전과 평가 상태를 조회합니다. 세션 쿠키를 보내고 versionId를 경로로 전달합니다.")
	@SecurityRequirement(name = "sessionAuth")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "문장 버전 정보"),
			@ApiResponse(responseCode = "401", ref = "#/components/responses/Unauthorized"),
			@ApiResponse(responseCode = "403", ref = "#/components/responses/Forbidden"),
			@ApiResponse(responseCode = "404", ref = "#/components/responses/NotFound")
	})
	SentenceVersionResponse getVersion(
			@Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser principal,
			@Parameter(description = "문장 버전 식별자", example = "84") @PathVariable Long versionId);

	@Operation(
			summary = "문장 삭제",
			description = "문장을 삭제 상태로 전환합니다. 세션 쿠키와 X-XSRF-TOKEN 헤더를 보내고 sentenceId를 경로로 전달합니다.")
	@SecurityRequirement(name = "sessionAuth")
	@SecurityRequirement(name = "csrfToken")
	@ApiResponses({
			@ApiResponse(responseCode = "204", description = "문장 삭제 완료"),
			@ApiResponse(responseCode = "401", ref = "#/components/responses/Unauthorized"),
			@ApiResponse(responseCode = "403", ref = "#/components/responses/Forbidden"),
			@ApiResponse(responseCode = "404", ref = "#/components/responses/NotFound")
	})
	void delete(
			@Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser principal,
			@Parameter(description = "문장 식별자", example = "42") @PathVariable Long sentenceId);
}
