package com.ssafy.c206.backend.user.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;

@Tag(name = "소셜 회원가입", description = "신규 OAuth 사용자의 닉네임과 저작권 동의를 확정합니다.")
public interface SocialRegistrationApi {

	@Operation(
			summary = "소셜 회원가입 대기 상태 조회",
			description = "OAuth 공급자 인증 후 서버 세션에 가입 대기 정보가 있는지만 반환합니다. 이메일과 공급자 식별자는 노출하지 않습니다.")
	@ApiResponse(responseCode = "200", description = "가입 대기 여부 조회 완료")
	SocialRegistrationPendingResponse pending(
			@Parameter(hidden = true) HttpServletRequest request);

	@Operation(
			summary = "소셜 회원가입 완료",
			description = "가입 대기 세션의 소셜 계정에 사용자가 선택한 닉네임과 현재 저작권 동의를 저장하고 로그인 세션을 생성합니다.")
	@SecurityRequirement(name = "csrfToken")
	@ApiResponses({
		@ApiResponse(responseCode = "201", description = "소셜 회원가입 및 로그인 완료"),
		@ApiResponse(responseCode = "400", ref = "#/components/responses/BadRequest"),
		@ApiResponse(responseCode = "401", ref = "#/components/responses/Unauthorized"),
		@ApiResponse(responseCode = "409", ref = "#/components/responses/Conflict"),
		@ApiResponse(responseCode = "503", ref = "#/components/responses/ServiceUnavailable")
	})
	UserResponse complete(
			@RequestBody(description = "닉네임과 현재 저작권 동의 정보", required = true)
			@Valid SocialRegistrationRequest registrationRequest,
			@Parameter(hidden = true) HttpServletRequest request,
			@Parameter(hidden = true) HttpServletResponse response);
}
