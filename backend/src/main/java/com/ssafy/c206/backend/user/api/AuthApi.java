package com.ssafy.c206.backend.user.api;

import com.ssafy.c206.backend.common.security.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;

@Tag(name = "인증 및 사용자", description = "회원 가입, 세션 인증, 현재 사용자 정보를 관리합니다.")
public interface AuthApi {

	@Operation(
			summary = "회원 가입",
			description = "새 사용자를 등록합니다. 가입 전에 /api/v1/email-verifications로 이메일 인증을 완료하고, 확인(confirm) 응답으로 받은 1회용 검증 토큰을 verificationToken 필드로 함께 제출해야 합니다. /api/v1/copyright-agreements/current에서 현재 동의서를 조회한 뒤 copyrightAgreementId와 copyrightAgreed=true도 제출해야 합니다. 제출 사이에 동의서가 변경되어 409(code: COPYRIGHT_AGREEMENT_MISMATCH)를 받으면 현재 동의서를 다시 조회·표시하고 재동의를 받아야 합니다. 활성 동의서를 제공할 수 없어 503(code: COPYRIGHT_AGREEMENT_UNAVAILABLE)을 받으면 잠시 후 현재 동의서 조회부터 다시 시도합니다. 이메일 인증 표식이 없거나 만료(10분)되었거나 토큰이 일치하지 않으면 403(code: EMAIL_NOT_VERIFIED)을 받으므로 인증부터 다시 진행합니다. 로그인하지 않은 사용자가 JSON 본문을 보내며, 요청 전에 CSRF 토큰을 발급받아 X-XSRF-TOKEN 헤더로 전송해야 합니다. 성공하면 생성된 사용자(201)를 받고 인증 표식은 소모됩니다. 입력값 오류는 400 응답의 fieldErrors를 수정해 재시도합니다.")
	@SecurityRequirement(name = "csrfToken")
	@ApiResponses({
			@ApiResponse(responseCode = "201", description = "회원 가입 완료"),
			@ApiResponse(responseCode = "400", ref = "#/components/responses/BadRequest"),
			@ApiResponse(responseCode = "409", ref = "#/components/responses/Conflict"),
			@ApiResponse(responseCode = "403", ref = "#/components/responses/Forbidden"),
			@ApiResponse(responseCode = "503", ref = "#/components/responses/ServiceUnavailable")
	})
	UserResponse register(
			@RequestBody(
					description = "이메일, 비밀번호, 별명, 이메일 검증 토큰과 현재 저작권 동의 정보를 포함하는 JSON 본문입니다.",
					required = true)
			@Valid SignupRequest request);

	@Operation(
			summary = "로그인",
			description = "이메일과 비밀번호로 세션을 생성합니다. 공개 엔드포인트이지만 상태를 변경하므로 먼저 /api/v1/csrf에서 받은 CSRF 토큰을 X-XSRF-TOKEN 헤더에 포함해야 합니다. JSON 본문으로 자격 증명을 전달하고 성공 시 현재 사용자(200)를 받습니다. 인증 실패는 401, CSRF 실패는 403 응답을 확인한 뒤 새 토큰 또는 자격 증명으로 재시도합니다.")
	@SecurityRequirement(name = "csrfToken")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "로그인 및 세션 생성 완료"),
			@ApiResponse(responseCode = "400", ref = "#/components/responses/BadRequest"),
			@ApiResponse(responseCode = "401", ref = "#/components/responses/Unauthorized"),
			@ApiResponse(responseCode = "403", ref = "#/components/responses/Forbidden")
	})
	UserResponse login(
			@RequestBody(
					description = "이메일과 비밀번호를 담은 로그인 JSON 본문입니다.",
					required = true)
			@Valid LoginRequest request,
			@Parameter(hidden = true) HttpServletRequest httpRequest,
			@Parameter(hidden = true) HttpServletResponse httpResponse);

	@Operation(
			summary = "로그아웃",
			description = "현재 세션과 CSRF 토큰을 폐기합니다. 로그인된 사용자가 호출하며 본문은 없습니다. 성공 시 204를 받고, 세션이 없으면 401, CSRF 토큰이 없거나 잘못되면 403을 확인한 뒤 로그인 또는 CSRF 발급부터 다시 진행합니다.")
	@SecurityRequirement(name = "sessionAuth")
	@SecurityRequirement(name = "csrfToken")
	@ApiResponses({
			@ApiResponse(responseCode = "204", description = "로그아웃 완료"),
			@ApiResponse(responseCode = "401", ref = "#/components/responses/Unauthorized"),
			@ApiResponse(responseCode = "403", ref = "#/components/responses/Forbidden")
	})
	void logout(
			@Parameter(hidden = true) Authentication authentication,
			@Parameter(hidden = true) HttpServletRequest request,
			@Parameter(hidden = true) HttpServletResponse response);

	@Operation(
			summary = "현재 사용자 조회",
			description = "로그인된 세션의 사용자 프로필을 조회합니다. 요청 본문 없이 세션 쿠키로 인증하며 성공 시 사용자 정보(200)를 받습니다. 401이면 로그인 후 다시 호출합니다. 403이면 계정이 비활성이므로 계정을 활성화한 뒤 다시 호출하세요.")
	@SecurityRequirement(name = "sessionAuth")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "현재 사용자 정보"),
			@ApiResponse(responseCode = "401", ref = "#/components/responses/Unauthorized"),
			@ApiResponse(responseCode = "403", ref = "#/components/responses/Forbidden")
	})
	UserResponse me(@Parameter(hidden = true) AuthenticatedUser principal);
}
