package com.ssafy.c206.backend.book.api;

import java.time.Instant;

import com.ssafy.c206.backend.book.domain.AiJob;
import com.ssafy.c206.backend.book.domain.AiJobStatus;
import io.swagger.v3.oas.annotations.media.Schema;

/** Deliberately excludes internal retry details and raw requester errors. */
public record AiJobStatusResponse(
		@Schema(description = "AI 작업 식별자입니다.", example = "31") Long jobId,
		@Schema(description = "책 생성을 요청한 보스전 식별자입니다.", example = "7") Long bossRunId,
		@Schema(description = "공개 가능한 AI 작업 상태입니다. PENDING은 대기, PROCESSING은 처리 중, RETRYING은 재시도 중, SUCCESS는 성공, FAILED는 실패입니다. 내부 요청자 오류는 제공하지 않습니다.", allowableValues = {"PENDING", "PROCESSING", "RETRYING", "SUCCESS", "FAILED"}, example = "PROCESSING") AiJobStatus status,
		@Schema(description = "작업 요청 시각입니다.", format = "date-time", example = "2026-07-27T10:00:00Z") Instant requestedAt,
		@Schema(description = "작업 상태 마지막 변경 시각입니다.", format = "date-time", example = "2026-07-27T10:01:00Z") Instant updatedAt) {
	public static AiJobStatusResponse from(AiJob job) {
		return new AiJobStatusResponse(job.getId(), job.getBossRun().getId(), job.getStatus(), job.getRequestedAt(),
				job.getUpdatedAt());
	}
}
