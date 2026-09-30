package com.ssafy.c206.backend.user.agreement.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "저작권 동의서", description = "회원가입에 사용할 현재 저작권 동의서를 조회합니다.")
public interface CopyrightAgreementApi {

	@Operation(
			summary = "현재 저작권 동의서 조회",
			description = "회원가입 전에 표시하고 동의받아야 하는 현재 활성 저작권 동의서를 반환합니다.")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "현재 동의서 조회 완료"),
			@ApiResponse(responseCode = "404", ref = "#/components/responses/NotFound")
	})
	CopyrightAgreementResponse current();
}
