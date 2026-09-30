package com.ssafy.c206.backend.book.api;

import java.time.Instant;

import com.ssafy.c206.backend.book.domain.Book;
import io.swagger.v3.oas.annotations.media.Schema;

public record MyContributedBookResponse(
		@Schema(description = "완성된 공동 책 식별자입니다.", example = "18") Long bookId,
		@Schema(description = "책이 생성된 보스런 식별자입니다.", example = "7") Long bossRunId,
		@Schema(description = "책의 보스 이름입니다.", example = "새벽의 수호자") String bossName,
		@Schema(description = "책별 표지 이미지 URL") String coverImageUrl,
		@Schema(description = "책 생성 완료 시각입니다.", format = "date-time") Instant completedAt,
		@Schema(description = "책에 포함된 전체 콘텐츠 수입니다.", minimum = "0", example = "12") int contentCount,
		@Schema(description = "이 책에 수록된 내 콘텐츠 수입니다.", minimum = "1", example = "2")
		int myIncludedContentCount) {

	public static MyContributedBookResponse from(Book book, String coverImageUrl, int contentCount,
			int myIncludedContentCount) {
		return new MyContributedBookResponse(book.getId(), book.getBossRun().getId(),
				book.getBossRun().getBoss().getName(), coverImageUrl, book.getCompletedAt(), contentCount,
				myIncludedContentCount);
	}
}
