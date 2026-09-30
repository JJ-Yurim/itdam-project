package com.ssafy.c206.backend.user.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

@Tag(name = "이메일 인증", description = "회원가입 전에 이메일 점유를 인증 코드로 확인합니다.")
public interface EmailVerificationApi {

	@Operation(
			summary = "인증 코드 발송",
			description = "입력한 이메일로 6자리 인증 코드를 발송합니다. 로그인하지 않은 사용자가 JSON 본문을 보내며, 요청 전에 CSRF 토큰을 발급받아 X-XSRF-TOKEN 헤더로 전송해야 합니다. 성공 시 코드 유효 시간과 재발송 대기 시간(202)을 받습니다. 같은 이메일로 재발송 대기 시간 안에 다시 요청하거나 같은 주소에서 너무 자주 요청하면 429를 받으므로 잠시 후 재시도합니다.")
	@SecurityRequirement(name = "csrfToken")
	@ApiResponses({
			@ApiResponse(responseCode = "202", description = "인증 코드 발송 접수"),
			@ApiResponse(responseCode = "400", ref = "#/components/responses/BadRequest"),
			@ApiResponse(responseCode = "403", ref = "#/components/responses/Forbidden"),
			@ApiResponse(responseCode = "429", ref = "#/components/responses/TooManyRequests")
	})
	EmailVerificationSendResponse send(
			@RequestBody(description = "인증 코드를 받을 이메일을 담은 JSON 본문입니다.", required = true)
			@Valid EmailVerificationSendRequest request,
			@Parameter(hidden = true) HttpServletRequest httpRequest);

	@Operation(
			summary = "인증 코드 확인",
			description = "메일로 받은 6자리 코드를 확인합니다. 성공 시 1회용 검증 토큰(200)을 받으며, 인증 완료 상태가 유지되는 10분 안에 이 토큰을 회원가입 요청의 verificationToken 필드로 제출해야 합니다. 코드가 틀리면 400을 받고 다시 입력하며, 5회 틀리면 코드가 무효화되어 429를 받으므로 재발송부터 다시 시작합니다. 코드가 만료되었거나 발송된 적이 없으면 404를 받습니다.")
	@SecurityRequirement(name = "csrfToken")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "이메일 인증 완료, 가입에 사용할 검증 토큰 발급"),
			@ApiResponse(responseCode = "400", ref = "#/components/responses/BadRequest"),
			@ApiResponse(responseCode = "403", ref = "#/components/responses/Forbidden"),
			@ApiResponse(responseCode = "404", ref = "#/components/responses/NotFound"),
			@ApiResponse(responseCode = "429", ref = "#/components/responses/TooManyRequests")
	})
	EmailVerificationConfirmResponse confirm(
			@RequestBody(description = "이메일과 6자리 인증 코드를 담은 JSON 본문입니다.", required = true)
			@Valid EmailVerificationConfirmRequest request);
}
