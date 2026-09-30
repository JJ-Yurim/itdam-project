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
		description = "보스전에서 생성되는 책의 상태를 폴링합니다.")
public interface BookGenerationApi {

	@Operation(
			summary = "책 생성 상태 조회",
			description = "보스전 결과로 만드는 책이 도서관에 공개될 준비가 되었는지 확인하도록 상태를 폴링합니다. "
					+ "JSESSIONID 세션 쿠키로 인증한 뒤 GET /api/v1/boss-runs/{bossRunId}/book-generation의 경로 변수로 "
					+ "보스전 식별자를 보냅니다. 성공하면 200과 BookGenerationStatusResponse를 받으며 생성 상태와 "
					+ "AI 작업 상태만 반환하고 내부 요청자 오류는 반환하지 않습니다. "
					+ "401이면 로그인, 403이면 계정을 활성화한 뒤 재시도하고, "
					+ "404이면 bossRunId를 보스전 목록에서 다시 확인하세요.")
	@SecurityRequirement(name = "sessionAuth")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "책 생성 상태"),
			@ApiResponse(responseCode = "401", ref = "#/components/responses/Unauthorized"),
			@ApiResponse(responseCode = "403", ref = "#/components/responses/Forbidden"),
			@ApiResponse(responseCode = "404", ref = "#/components/responses/NotFound")
	})
	BookGenerationStatusResponse getStatus(
			@Parameter(hidden = true) AuthenticatedUser principal,
			@Parameter(description = "보스전 식별자입니다.", example = "7") Long bossRunId);
}
