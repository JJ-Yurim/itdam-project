package com.ssafy.c206.backend.sentence.evaluation.api;

import java.time.LocalDate;
import io.swagger.v3.oas.annotations.media.Schema;

public record EvaluationUsageResponse(
		@Schema(description = "사용량을 계산한 서비스 날짜입니다. ISO-8601 date 형식입니다.", format = "date", example = "2026-07-27") LocalDate serviceDate,
		@Schema(description = "해당 날짜에 이미 사용한 평가 횟수입니다.", minimum = "0", example = "3") int usedCount,
		@Schema(description = "해당 날짜에 허용된 평가 횟수 한도입니다.", minimum = "0", example = "10") int limit,
		@Schema(description = "해당 날짜에 남은 평가 횟수입니다.", minimum = "0", example = "7") int remainingCount) { }
