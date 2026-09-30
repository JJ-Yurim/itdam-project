package com.ssafy.c206.backend.sentence.archive.api;

import java.time.Instant;

import com.ssafy.c206.backend.sentence.domain.SentenceVersion;
import com.ssafy.c206.backend.sentence.domain.EvaluationStatus;
import io.swagger.v3.oas.annotations.media.Schema;

public record UnevaluatedArchiveSentenceResponse(
		@Schema(description = "문장 버전 식별자입니다.", example = "84") Long sentenceVersionId,
		@Schema(description = "문장 식별자입니다.", example = "42") Long sentenceId,
		@Schema(description = "문장 내부 버전 번호입니다.", minimum = "1", example = "2") int versionNo,
		@Schema(description = "문장 내용입니다.", example = "비가 그친 골목에 햇빛이 천천히 번졌다.") String content,
		@Schema(
				description = "평가 진행 상태입니다. 이 API에서는 DRAFT, EVALUATING, EVALUATION_FAILED만 반환합니다.",
				example = "DRAFT")
		EvaluationStatus evaluationStatus,
		@Schema(description = "문장 버전을 만든 시각입니다.", format = "date-time", example = "2026-07-27T01:23:45Z")
		Instant createdAt) {

	public static UnevaluatedArchiveSentenceResponse from(SentenceVersion version) {
		return new UnevaluatedArchiveSentenceResponse(
				version.getId(),
				version.getSentence().getId(),
				version.getVersionNo(),
				version.getContent(),
				version.getEvaluationStatus(),
				version.getCreatedAt());
	}
}
