package com.ssafy.c206.backend.book.api;

import com.ssafy.c206.backend.book.domain.BookContent;
import io.swagger.v3.oas.annotations.media.Schema;

public record BookInclusionSummary(
		@Schema(description = "문장 버전이 수록된 생성 완료 책 식별자입니다.", example = "18") Long bookId,
		@Schema(description = "책 안의 수록 순서입니다.", minimum = "1", example = "3") int sequenceNo) {
	public static BookInclusionSummary from(BookContent content) {
		return new BookInclusionSummary(content.getBook().getId(), content.getSequenceNo());
	}
}
