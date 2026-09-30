package com.ssafy.c206.backend.book.api;

import java.time.Instant;

import io.swagger.v3.oas.annotations.media.Schema;

public record AdminBookCoverResponse(
		Long bookId,
		Long coverImageAssetId,
		String coverImageUrl,
		/*
		 * 표지 교체도 책 행을 수정하므로 updatedAt이 바뀐다. 이 값을 돌려주지 않으면 호출자가
		 * 교체 직후 공개 상태 전환에 옛 expectedUpdatedAt을 실어 보내 409를 맞는다.
		 */
		@Schema(description = "동시 수정 감지에 사용하는 마지막 수정 시각입니다.", format = "date-time")
		Instant updatedAt) {
}
