package com.ssafy.c206.backend.boss.submission.api;

import java.time.Instant;

import com.ssafy.c206.backend.boss.submission.domain.BossSubmission;
import io.swagger.v3.oas.annotations.media.Schema;

public record BossSubmissionHistoryItem(
		@Schema(description = "제출 식별자입니다.", example = "55") Long submissionId,
		@Schema(description = "제출 당시 보스 페이즈 식별자입니다.", example = "21") Long bossPhaseId,
		@Schema(description = "제출한 문장 버전 식별자입니다.", example = "84") Long sentenceVersionId,
		@Schema(description = "최종 반영 피해입니다.", minimum = "0", example = "57") long finalDamage,
		@Schema(description = "제출 시각입니다.", format = "date-time", example = "2026-07-27T10:30:00Z") Instant submittedAt) {

	public static BossSubmissionHistoryItem from(BossSubmission submission) {
		return new BossSubmissionHistoryItem(submission.getId(), submission.getBossPhase().getId(),
				submission.getSentenceVersion().getId(), submission.getFinalDamage(), submission.getCreatedAt());
	}
}
