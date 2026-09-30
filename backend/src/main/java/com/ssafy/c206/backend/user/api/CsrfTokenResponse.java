package com.ssafy.c206.backend.user.api;

import io.swagger.v3.oas.annotations.media.Schema;

public record CsrfTokenResponse(
		@Schema(description = "상태 변경 요청의 X-XSRF-TOKEN 헤더에 넣을 CSRF 토큰입니다.", example = "3b7c9b1e-6d3b-4e89-aed1-0d4f4a5f2d91")
		String token) {
}
