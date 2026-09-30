package com.ssafy.c206.backend.sentence.api;

import java.time.Instant;

import com.ssafy.c206.backend.sentence.domain.SentenceVersion;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

public record SentenceVersionResponse(
		@Schema(description = "문장 버전 식별자입니다.", example = "84") Long id,
		@Schema(description = "이 버전이 속한 문장 식별자입니다.", example = "42") Long sentenceId,
		@Schema(description = "문장 안에서 증가하는 버전 번호입니다.", minimum = "1", example = "2") int versionNo,
		@Schema(description = "해당 버전에 저장된 문장 내용입니다.", example = "비가 그친 골목에 햇빛이 천천히 번졌다.") String content,
		@Schema(description = "평가 진행 상태입니다. DRAFT는 초안, EVALUATING은 진행 중, EVALUATED는 완료, REJECTED는 안전성 거부, EVALUATION_FAILED는 기술 실패입니다.", allowableValues = {"DRAFT", "EVALUATING", "EVALUATED", "REJECTED", "EVALUATION_FAILED"}, example = "DRAFT") String evaluationStatus,
		@Schema(description = "버전을 만든 시각입니다. ISO-8601 UTC date-time 형식입니다.", format = "date-time", example = "2026-07-27T01:23:45Z") Instant createdAt,
		@Schema(description = "평가가 확정된 시각입니다. 미확정이면 null이며 ISO-8601 UTC date-time 형식입니다.", format = "date-time", example = "2026-07-27T02:23:45Z", nullable = true) Instant confirmedAt) {

	public static SentenceVersionResponse from(SentenceVersion version) {
		return new SentenceVersionResponse(version.getId(), version.getSentence().getId(), version.getVersionNo(), version.getContent(),
				version.getEvaluationStatus().name(), version.getCreatedAt(), version.getConfirmedAt());
	}

	@JsonProperty("sentenceVersionId")
	@Schema(description = "id와 같은 문장 버전 식별자의 호환용 이름입니다.", example = "84")
	public Long sentenceVersionId() {
		return id;
	}
}
