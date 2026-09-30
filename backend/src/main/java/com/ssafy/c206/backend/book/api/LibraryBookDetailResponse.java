package com.ssafy.c206.backend.book.api;

import java.time.Instant;
import java.util.List;

import com.ssafy.c206.backend.book.domain.Book;
import io.swagger.v3.oas.annotations.media.Schema;

public record LibraryBookDetailResponse(
		@Schema(description = "생성 완료 책 식별자입니다.", example = "18") Long bookId,
		@Schema(description = "책이 생성된 보스전 식별자입니다.", example = "7") Long bossRunId,
		@Schema(description = "책의 보스전 이름입니다.", example = "새벽의 수호자") String bossName,
		@Schema(description = "책별 표지 이미지 URL") String coverImageUrl,
		@Schema(description = "책 생성 완료 시각입니다.", format = "date-time", example = "2026-07-27T10:05:00Z") Instant completedAt,
		@Schema(description = "독자가 읽는 완성된 전문입니다. 문단은 빈 줄로 구분됩니다. "
				+ "본문 계약 이전에 생성됐고 수록 콘텐츠도 없는 책은 null입니다.", nullable = true,
				example = "그 겨울, 도시는 낮게 숨을 쉬었다.\n\n안개가 낀 새벽, 나는 인적 없는 길을 천천히 걸었다.") String body,
		@Schema(description = "책이 받은 좋아요 수입니다.", minimum = "0", example = "12") long likeCount,
		@Schema(description = "현재 사용자가 이 책에 좋아요를 눌렀는지 여부입니다. 비회원은 항상 false입니다.", example = "false") boolean liked,
		@Schema(description = "현재 사용자가 이 책을 북마크했는지 여부입니다. 비회원은 항상 false입니다.", example = "false") boolean bookmarked,
		@Schema(description = "책에 수록된 콘텐츠 순서 목록입니다. 기여자 표기의 근거이며 본문 표시는 body를 사용합니다.")
		List<BookContentResponse> contents) {
	public static LibraryBookDetailResponse from(Book book, List<BookContentResponse> contents, String coverImageUrl,
			long likeCount, boolean liked, boolean bookmarked) {
		return new LibraryBookDetailResponse(book.getId(), book.getBossRun().getId(), book.getBossRun().getBoss().getName(),
				coverImageUrl, book.getCompletedAt(), book.getBody(), likeCount, liked, bookmarked, contents);
	}
}
