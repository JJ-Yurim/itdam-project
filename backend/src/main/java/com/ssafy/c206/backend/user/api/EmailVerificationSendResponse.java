package com.ssafy.c206.backend.user.api;

import io.swagger.v3.oas.annotations.media.Schema;

public record EmailVerificationSendResponse(
		@Schema(description = "인증 코드의 유효 시간(초)입니다.", example = "300")
		long expiresInSeconds,
		@Schema(description = "같은 이메일로 재발송이 가능해질 때까지의 시간(초)입니다.", example = "60")
		long resendCooldownSeconds) {
}
