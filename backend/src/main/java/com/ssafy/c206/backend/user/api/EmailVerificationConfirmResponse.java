package com.ssafy.c206.backend.user.api;

import com.ssafy.c206.backend.user.service.EmailVerificationService;
import io.swagger.v3.oas.annotations.media.Schema;

public record EmailVerificationConfirmResponse(
		@Schema(description = "가입 요청에 함께 제출할 1회용 검증 토큰입니다. 인증 완료 상태가 유지되는 동안만 유효합니다.",
				example = "5b1f9c1e-8d1a-4c6e-9b3f-2a7d8e4f6c01")
		String verificationToken,
		@Schema(description = "검증 토큰과 인증 완료 상태의 유효 시간(초)입니다. 이 시간 안에 가입을 완료해야 합니다.", example = "600")
		long validForSeconds) {

	public static EmailVerificationConfirmResponse from(EmailVerificationService.ConfirmResult result) {
		return new EmailVerificationConfirmResponse(result.verificationToken(), result.validForSeconds());
	}
}
