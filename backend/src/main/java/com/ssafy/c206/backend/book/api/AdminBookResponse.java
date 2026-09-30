package com.ssafy.c206.backend.book.api;

import java.time.Instant;

import com.ssafy.c206.backend.book.domain.Book;
import com.ssafy.c206.backend.book.domain.BookStatus;
import com.ssafy.c206.backend.book.domain.BookVisibility;
import com.ssafy.c206.backend.boss.image.service.BossImageUrlResolver;
import io.swagger.v3.oas.annotations.media.Schema;

public record AdminBookResponse(
		@Schema(description = "책 식별자입니다.", example = "18") Long bookId,
		@Schema(description = "책이 생성된 보스런 식별자입니다.", example = "7") Long bossRunId,
		@Schema(description = "책의 보스 이름입니다.", example = "새벽의 수호자") String bossName,
		@Schema(description = "책 생성 상태입니다.") BookStatus status,
		@Schema(description = "서비스 공개 여부입니다.") BookVisibility visibility,
		@Schema(description = "표지 이미지 자산 ID입니다. 없으면 null입니다.", nullable = true) Long coverImageAssetId,
		@Schema(description = "표지 이미지 공개 URL입니다. 없으면 null입니다.", nullable = true) String coverImageUrl,
		@Schema(description = "생성 완료 시각입니다. 미완성이면 null입니다.", format = "date-time", nullable = true) Instant completedAt,
		@Schema(description = "책에 포함된 콘텐츠 수입니다.", minimum = "0", example = "8") int contentCount,
		@Schema(description = "동시 수정 감지에 사용하는 마지막 수정 시각입니다.", format = "date-time") Instant updatedAt) {

	public static AdminBookResponse from(Book book, int contentCount, BossImageUrlResolver resolver) {
		return new AdminBookResponse(book.getId(), book.getBossRun().getId(), book.getBossRun().getBoss().getName(),
				book.getStatus(), book.getVisibility(),
				book.getCoverImageAsset() == null ? null : book.getCoverImageAsset().getId(),
				resolver.publicUrlOrNull(book.getCoverImageAsset()),
				book.getCompletedAt(), contentCount, book.getUpdatedAt());
	}
}
