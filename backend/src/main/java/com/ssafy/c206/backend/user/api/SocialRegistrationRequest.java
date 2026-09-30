package com.ssafy.c206.backend.user.api;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record SocialRegistrationRequest(
		@NotBlank @Size(max = 100) String nickname,
		@NotNull Long copyrightAgreementId,
		@AssertTrue Boolean copyrightAgreed) {
}
