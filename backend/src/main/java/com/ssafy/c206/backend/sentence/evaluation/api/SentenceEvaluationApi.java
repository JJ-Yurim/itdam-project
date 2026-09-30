package com.ssafy.c206.backend.sentence.evaluation.api;

import com.ssafy.c206.backend.common.security.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;

@Tag(
		name = "문장 평가",
		description = "문장 버전을 동기적으로 평가하고 오늘의 평가 사용량을 조회합니다.")
public interface SentenceEvaluationApi {

	@Operation(
			summary = "문장 버전 평가",
			description = "문장 버전을 동기적으로 평가해 안전성, 점수, 피드백을 반환합니다. 로그인 세션과 CSRF 토큰이 필요하고 versionId를 경로로 전달하며 본문은 없습니다. 409는 현재 평가 상태를 조회해 충돌을 해소하고, 422는 안전성 거부 사유에 맞게 문장을 수정하며, 429는 일일 한도 초기화 뒤 재시도하고, 500은 잠시 후 다시 시도합니다.")
	@SecurityRequirement(name = "sessionAuth")
	@SecurityRequirement(name = "csrfToken")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "동기 평가 완료 또는 현재 평가 상태"),
			@ApiResponse(responseCode = "401", ref = "#/components/responses/Unauthorized"),
			@ApiResponse(responseCode = "403", ref = "#/components/responses/Forbidden"),
			@ApiResponse(responseCode = "404", ref = "#/components/responses/NotFound"),
			@ApiResponse(responseCode = "409", ref = "#/components/responses/Conflict"),
			@ApiResponse(responseCode = "422", ref = "#/components/responses/UnprocessableEntity"),
			@ApiResponse(responseCode = "429", ref = "#/components/responses/TooManyRequests"),
			@ApiResponse(responseCode = "500", ref = "#/components/responses/InternalServerError")
	})
	SentenceEvaluationResponse evaluate(
			@Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser principal,
			@Parameter(description = "평가할 문장 버전 식별자입니다.", example = "84")
			@PathVariable Long versionId);

	@Operation(
			summary = "문장 버전 평가 결과 조회",
			description = "이미 요청한 문장 버전의 평가 상태와 결과를 조회합니다. 세션 쿠키로 인증하고 versionId를 경로로 전달하며 본문은 없습니다. 성공 시 평가 결과(200)를 받고, 404는 버전 식별자 또는 소유 권한을 확인한 뒤 재시도합니다.")
	@SecurityRequirement(name = "sessionAuth")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "문장 버전 평가 상태 또는 결과"),
			@ApiResponse(responseCode = "401", ref = "#/components/responses/Unauthorized"),
			@ApiResponse(responseCode = "404", ref = "#/components/responses/NotFound")
	})
	SentenceEvaluationResponse get(
			@Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser principal,
			@Parameter(description = "조회할 문장 버전 식별자입니다.", example = "84")
			@PathVariable Long versionId);

	@Operation(
			summary = "오늘의 평가 사용량 조회",
			description = "로그인한 사용자의 서비스 날짜 기준 평가 사용량과 남은 횟수를 조회합니다. 세션 쿠키로 인증하며 본문과 추가 입력은 없습니다. 성공 시 사용량(200)을 받고, 401이면 로그인 후 다시 호출합니다.")
	@SecurityRequirement(name = "sessionAuth")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "오늘의 평가 사용량"),
			@ApiResponse(responseCode = "401", ref = "#/components/responses/Unauthorized")
	})
	EvaluationUsageResponse usage(
			@Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser principal);
}
