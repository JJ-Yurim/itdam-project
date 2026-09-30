package com.ssafy.c206.backend.user.api;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record EmailVerificationConfirmRequest(
		@Schema(description = "인증 코드를 받은 이메일 주소입니다.", example = "writer@example.com")
		@NotBlank @Email @Size(max = 255) String email,
		@Schema(description = "메일로 받은 6자리 숫자 인증 코드입니다.", example = "483920")
		@NotBlank @Pattern(regexp = "\\d{6}", message = "code must be a 6-digit number") String code) {
}
