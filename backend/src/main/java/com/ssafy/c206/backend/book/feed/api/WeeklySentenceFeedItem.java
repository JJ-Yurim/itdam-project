package com.ssafy.c206.backend.book.feed.api;

import com.ssafy.c206.backend.book.domain.BookContent;
import com.ssafy.c206.backend.book.feed.domain.WeeklySentenceSetItem;
import io.swagger.v3.oas.annotations.media.Schema;

public record WeeklySentenceFeedItem(
		@Schema(description = "문장이 수록된 책 콘텐츠 식별자입니다. 좋아요 등록·해제에 사용합니다.", example = "42")
		Long bookContentId,
		@Schema(description = "선정된 문장 본문입니다.", example = "새벽의 수호자가 마지막 문장을 완성했다.") String content,
		@Schema(description = "문장 작성자의 닉네임입니다.", example = "글쓰는고양이") String authorNickname,
		@Schema(description = "문장이 수록된 완성 공동 책 식별자입니다.", example = "18") Long bookId,
		@Schema(description = "책의 보스 이름입니다.", example = "새벽의 수호자") String bossName,
		@Schema(description = "이 문장이 받은 좋아요 수입니다.", minimum = "0", example = "7") long likeCount,
		@Schema(description = "현재 사용자가 이 문장에 좋아요를 눌렀는지 여부입니다.", example = "false") boolean liked) {

	public static WeeklySentenceFeedItem from(WeeklySentenceSetItem item, long likeCount, boolean liked) {
		BookContent content = item.getBookContent();
		String authorNickname = content.getAuthor() == null ? null : content.getAuthor().getNickname();
		return new WeeklySentenceFeedItem(content.getId(), content.getContent(), authorNickname,
				content.getBook().getId(), content.getBook().getBossRun().getBoss().getName(), likeCount, liked);
	}
}
