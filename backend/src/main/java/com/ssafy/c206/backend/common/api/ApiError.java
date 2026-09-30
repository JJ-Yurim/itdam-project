package com.ssafy.c206.backend.common.api;

import java.time.Instant;
import java.util.List;
import io.swagger.v3.oas.annotations.media.Schema;

public record ApiError(
		@Schema(description = "오류가 발생한 시각입니다. ISO-8601 UTC date-time 형식입니다.", format = "date-time", example = "2026-07-27T02:23:45Z") Instant occurredAt,
		@Schema(description = "프로그램이 처리할 오류 코드입니다.", example = "VALIDATION_ERROR") String code,
		@Schema(description = "사용자에게 표시할 오류 메시지입니다.", example = "요청 값을 확인한 뒤 다시 시도하세요.") String message,
		@Schema(description = "입력값 검증에 실패한 필드별 오류 목록입니다.", example = "[{\"field\":\"content\",\"reason\":\"content must not be blank\"}]") List<FieldError> fieldErrors) {

	public record FieldError(
			@Schema(description = "검증에 실패한 요청 필드 이름입니다.", example = "content") String field,
			@Schema(description = "해당 필드가 규칙을 만족하지 못한 이유입니다.", example = "content must not be blank") String reason) {
	}
}
