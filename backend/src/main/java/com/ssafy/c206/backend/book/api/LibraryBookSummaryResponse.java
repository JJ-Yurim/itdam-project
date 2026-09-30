package com.ssafy.c206.backend.book.api;

import java.time.Instant;

import com.ssafy.c206.backend.book.domain.Book;
import io.swagger.v3.oas.annotations.media.Schema;

public record LibraryBookSummaryResponse(
		@Schema(description = "생성 완료 책 식별자입니다.", example = "18") Long bookId,
		@Schema(description = "책이 생성된 보스전 식별자입니다.", example = "7") Long bossRunId,
		@Schema(description = "책의 보스전 이름입니다.", example = "새벽의 수호자") String bossName,
		@Schema(description = "책별 표지 이미지 URL") String coverImageUrl,
		@Schema(description = "책 생성 완료 시각입니다.", format = "date-time", example = "2026-07-27T10:05:00Z") Instant completedAt,
		@Schema(description = "책에 포함된 콘텐츠 수입니다.", minimum = "0", example = "8") int contentCount,
		@Schema(description = "책 내용을 3~4문장으로 요약한 미리보기입니다. 요약 계약 이전에 생성된 책은 null입니다.",
				nullable = true, example = "겨울 도시의 새벽에서 이야기가 시작된다. 인물은 인적 없는 길을 걸으며 잊힌 것을 떠올린다.") String summary,
		@Schema(description = "책이 받은 좋아요 수입니다.", minimum = "0", example = "12") long likeCount,
		@Schema(description = "현재 사용자가 이 책에 좋아요를 눌렀는지 여부입니다. 비회원은 항상 false입니다.", example = "false") boolean liked,
		@Schema(description = "현재 사용자가 이 책을 북마크했는지 여부입니다. 비회원은 항상 false입니다.", example = "false") boolean bookmarked) {
	public static LibraryBookSummaryResponse from(Book book, int contentCount, String coverImageUrl, long likeCount,
			boolean liked, boolean bookmarked) {
		return new LibraryBookSummaryResponse(book.getId(), book.getBossRun().getId(), book.getBossRun().getBoss().getName(),
				coverImageUrl, book.getCompletedAt(), contentCount, book.getSummary(), likeCount, liked, bookmarked);
	}
}
