package com.ssafy.c206.backend.user.api;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import io.swagger.v3.oas.annotations.media.Schema;

public record LoginRequest(
		@Schema(description = "가입한 이메일 주소. 공백 없이 이메일 형식이어야 합니다.", example = "writer@example.com")
		@NotBlank @Email String email,
		@Schema(description = "가입 시 설정한 비밀번호. 공백일 수 없습니다.", example = "Sentence8!")
		@NotBlank String password) {
}
