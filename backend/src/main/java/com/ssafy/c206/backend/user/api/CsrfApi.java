package com.ssafy.c206.backend.user.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Tag(name = "인증 및 사용자", description = "회원 가입, 세션 인증, 현재 사용자 정보를 관리합니다.")
public interface CsrfApi {

	@Operation(
			summary = "CSRF 토큰 발급",
			description = "공개 엔드포인트로, 이후 POST·PATCH·DELETE 요청에 필요한 CSRF 토큰을 발급합니다. 본문 없이 호출하고 반환된 token을 X-XSRF-TOKEN 헤더에 넣습니다. 성공 시 토큰(200)을 받으며 만료 또는 거부 시 새 토큰을 발급받아 재시도합니다.")
	@ApiResponses(@ApiResponse(responseCode = "200", description = "CSRF 토큰 발급 완료"))
	CsrfTokenResponse token(
			@Parameter(hidden = true) HttpServletRequest request,
			@Parameter(hidden = true) HttpServletResponse response);
}
