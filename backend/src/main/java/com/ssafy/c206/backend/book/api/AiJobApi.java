package com.ssafy.c206.backend.book.api;

import com.ssafy.c206.backend.common.security.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(
		name = "책 생성",
		description = "AI 책 생성 작업의 공개 가능한 상태를 조회합니다.")
public interface AiJobApi {

	@Operation(
			summary = "AI 작업 상태 조회",
			description = "책 생성 비동기 작업이 끝났는지 확인하도록 상태를 폴링합니다. "
					+ "JSESSIONID 세션 쿠키로 인증한 뒤 GET /api/v1/ai-jobs/{jobId}의 경로 변수로 작업 식별자를 보냅니다. "
					+ "성공하면 200과 AiJobStatusResponse를 받으며 PENDING, PROCESSING, RETRYING, SUCCESS, FAILED만 노출되고 "
					+ "내부 요청자 오류나 재시도 상세는 노출하지 않습니다. "
					+ "401이면 로그인, 403이면 계정을 활성화한 뒤 재시도하고, "
					+ "404이면 jobId를 책 생성 상태 응답에서 다시 확인하세요.")
	@SecurityRequirement(name = "sessionAuth")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "AI 작업 상태"),
			@ApiResponse(responseCode = "401", ref = "#/components/responses/Unauthorized"),
			@ApiResponse(responseCode = "403", ref = "#/components/responses/Forbidden"),
			@ApiResponse(responseCode = "404", ref = "#/components/responses/NotFound")
	})
	AiJobStatusResponse get(
			@Parameter(hidden = true) AuthenticatedUser principal,
			@Parameter(description = "AI 작업 식별자입니다.", example = "31") Long jobId);
}
