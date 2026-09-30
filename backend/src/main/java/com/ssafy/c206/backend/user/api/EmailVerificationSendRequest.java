package com.ssafy.c206.backend.user.api;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record EmailVerificationSendRequest(
		@Schema(description = "인증 코드를 받을 이메일 주소. 공백 없이 이메일 형식이며 최대 255자입니다.", example = "writer@example.com")
		@NotBlank @Email @Size(max = 255) String email) {
}
