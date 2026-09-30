package com.ssafy.c206.backend.book.feed.api;

import java.time.LocalDate;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

public record WeeklySentenceFeedResponse(
		@Schema(description = "이 세트가 노출되는 주의 시작일(월요일)입니다.", format = "date", example = "2026-08-03")
		LocalDate weekStartDate,
		@Schema(description = "이번 주 선정 문장 목록입니다. 한 주 동안 동일하게 유지됩니다.")
		List<WeeklySentenceFeedItem> items) {
}
