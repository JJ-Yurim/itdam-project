package com.ssafy.c206.backend.user.api;

import java.time.Instant;

import com.ssafy.c206.backend.user.domain.User;
import com.ssafy.c206.backend.user.domain.UserRole;
import com.ssafy.c206.backend.user.domain.UserStatus;
import io.swagger.v3.oas.annotations.media.Schema;

public record UserResponse(
		@Schema(description = "사용자 식별자입니다.", example = "42") Long id,
		@Schema(description = "사용자 이메일 주소입니다.", example = "writer@example.com") String email,
		@Schema(description = "서비스에 표시할 별명입니다.", example = "문장여행자") String nickname,
		@Schema(description = "사용자 상태입니다. ACTIVE는 활성 사용자, INACTIVE는 비활성 사용자를 뜻합니다.", allowableValues = {"ACTIVE", "INACTIVE"}, example = "ACTIVE") UserStatus status,
		@Schema(description = "사용자 역할입니다. 프론트엔드는 이 값으로 관리자 메뉴 노출을 제어합니다.", allowableValues = {"USER", "ADMIN"}, example = "USER") UserRole role,
		@Schema(description = "가입 시각입니다. ISO-8601 UTC date-time 형식입니다.", format = "date-time", example = "2026-07-27T01:23:45Z") Instant createdAt) {

	public static UserResponse from(User user) {
		return new UserResponse(user.getId(), user.getEmail(), user.getNickname(), user.getStatus(), user.getRole(), user.getCreatedAt());
	}
}
