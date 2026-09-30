package com.ssafy.c206.backend.book.api;

import java.time.Instant;

import com.ssafy.c206.backend.book.domain.AiJob;
import com.ssafy.c206.backend.book.domain.AiJobStatus;
import com.ssafy.c206.backend.book.domain.Book;
import com.ssafy.c206.backend.book.domain.BookStatus;
import io.swagger.v3.oas.annotations.media.Schema;

public record BookGenerationStatusResponse(
		@Schema(description = "책 생성을 조회한 보스전 식별자입니다.", example = "7") Long bossRunId,
		@Schema(description = "생성 대상 책 식별자입니다.", example = "18") Long bookId,
		@Schema(description = "책 생성 상태입니다. GENERATING은 생성 중, COMPLETED는 도서관 공개 가능, FAILED는 생성 실패입니다.", allowableValues = {"GENERATING", "COMPLETED", "FAILED"}, example = "GENERATING") BookStatus bookStatus,
		@Schema(description = "연결된 AI 작업 식별자입니다.", example = "31") Long jobId,
		@Schema(description = "AI 작업 워크플로 상태입니다. PENDING은 대기열에서 시작 대기, PROCESSING은 생성 처리 중, RETRYING은 실패 후 재시도 대기 또는 실행 중, SUCCESS는 작업 성공, FAILED는 재시도 후 최종 실패입니다. 내부 요청자 오류는 제공하지 않습니다.", allowableValues = {"PENDING", "PROCESSING", "RETRYING", "SUCCESS", "FAILED"}, example = "PROCESSING") AiJobStatus jobStatus,
		@Schema(description = "생성 작업 요청 시각입니다.", format = "date-time", example = "2026-07-27T10:00:00Z") Instant requestedAt,
		@Schema(description = "책 생성 완료 시각입니다. 완료 전이면 null입니다.", format = "date-time", nullable = true, example = "2026-07-27T10:05:00Z") Instant completedAt,
		@Schema(description = "작업 상태 마지막 변경 시각입니다.", format = "date-time", example = "2026-07-27T10:01:00Z") Instant updatedAt) {
	public static BookGenerationStatusResponse from(Book book, AiJob job) {
		return new BookGenerationStatusResponse(book.getBossRun().getId(), book.getId(), book.getStatus(), job.getId(),
				job.getStatus(), job.getRequestedAt(), book.getCompletedAt(), job.getUpdatedAt());
	}
}
