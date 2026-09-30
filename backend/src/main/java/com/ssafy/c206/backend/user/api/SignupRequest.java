package com.ssafy.c206.backend.user.api;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import io.swagger.v3.oas.annotations.media.Schema;

public record SignupRequest(
		@Schema(description = "가입에 사용할 이메일 주소. 공백 없이 이메일 형식이며 최대 255자입니다.", example = "writer@example.com")
		@NotBlank @Email @Size(max = 255) String email,
		@Schema(description = "로그인 비밀번호. 공백이 아니고 8~72자여야 합니다.", example = "Sentence8!")
		@NotBlank @Size(min = 8, max = 72) String password,
		@Schema(description = "서비스에 표시할 별명. 공백이 아니고 1~100자여야 합니다.", example = "문장여행자")
		@NotBlank @Size(min = 1, max = 100) String nickname,
		@Schema(description = "이메일 인증 확인(/email-verifications/confirm) 응답으로 받은 1회용 검증 토큰입니다.",
				example = "5b1f9c1e-8d1a-4c6e-9b3f-2a7d8e4f6c01")
		@NotBlank @Size(max = 64) String verificationToken,
		@Schema(description = "현재 활성 저작권 동의서 식별자입니다.", example = "1")
		@NotNull Long copyrightAgreementId,
		@Schema(description = "현재 저작권 동의서에 동의했는지 여부입니다. 회원가입에는 true가 필요합니다.", example = "true")
		@NotNull @AssertTrue Boolean copyrightAgreed) {
}
