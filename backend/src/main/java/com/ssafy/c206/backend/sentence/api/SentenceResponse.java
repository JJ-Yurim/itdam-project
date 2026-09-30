package com.ssafy.c206.backend.sentence.api;

import java.time.Instant;
import java.util.List;

import com.ssafy.c206.backend.sentence.domain.Sentence;
import io.swagger.v3.oas.annotations.media.Schema;

public record SentenceResponse(
		@Schema(description = "문장 식별자입니다.", example = "42") Long id,
		@Schema(description = "문장 상태입니다. ACTIVE는 사용 가능, DELETED는 삭제 상태입니다.", allowableValues = {"ACTIVE", "DELETED"}, example = "ACTIVE") String status,
		@Schema(description = "삭제 여부입니다. true면 삭제 상태입니다.", example = "false") boolean deleted,
		@Schema(description = "문장을 만든 시각입니다. ISO-8601 UTC date-time 형식입니다.", format = "date-time", example = "2026-07-27T01:23:45Z") Instant createdAt,
		@Schema(description = "문장을 마지막으로 수정한 시각입니다. ISO-8601 UTC date-time 형식입니다.", format = "date-time", example = "2026-07-27T02:23:45Z") Instant updatedAt,
		@Schema(description = "삭제 시각입니다. 삭제되지 않은 문장은 null이며 ISO-8601 UTC date-time 형식입니다.", format = "date-time", example = "2026-07-27T03:23:45Z", nullable = true) Instant deletedAt,
		@Schema(description = "이 문장에 속한 버전 목록입니다.", example = "[{\"id\":84,\"versionNo\":2}]") List<SentenceVersionResponse> versions) {

	public static SentenceResponse from(Sentence sentence, List<SentenceVersionResponse> versions) {
		return new SentenceResponse(sentence.getId(), sentence.getStatus().name(), !sentence.isActive(),
				sentence.getCreatedAt(), sentence.getUpdatedAt(), sentence.getDeletedAt(), versions);
	}
}
