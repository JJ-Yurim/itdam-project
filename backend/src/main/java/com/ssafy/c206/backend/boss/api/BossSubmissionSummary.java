package com.ssafy.c206.backend.boss.api;

import java.time.Instant;

import com.ssafy.c206.backend.boss.submission.domain.BossSubmission;
import io.swagger.v3.oas.annotations.media.Schema;

public record BossSubmissionSummary(
		@Schema(description = "제출 식별자입니다.", example = "55") Long submissionId,
		@Schema(description = "제출한 보스전 식별자입니다.", example = "7") Long bossRunId,
		@Schema(description = "제출한 보스 이름입니다.", example = "새벽의 수호자") String bossName,
		@Schema(description = "피해가 반영된 보스 페이즈 식별자입니다.", example = "21") Long bossPhaseId,
		@Schema(description = "제출한 문장 버전 식별자입니다.", example = "84") Long sentenceVersionId,
		@Schema(description = "제출한 문장 내용입니다. 제출 시점의 문장 버전은 불변이므로 이후에도 바뀌지 않습니다.", example = "햇빛이 창가에 부드럽게 번졌다.") String content,
		@Schema(description = "최종 반영 피해입니다.", minimum = "0", example = "57") long finalDamage,
		@Schema(description = "제출 시각입니다. ISO-8601 UTC date-time 형식입니다.", format = "date-time", example = "2026-07-27T10:30:00Z") Instant submittedAt) {
	public static BossSubmissionSummary from(BossSubmission submission) {
		return new BossSubmissionSummary(submission.getId(), submission.getBossRun().getId(), submission.getBossRun().getBoss().getName(), submission.getBossPhase().getId(),
				submission.getSentenceVersion().getId(), submission.getSentenceVersion().getContent(),
				submission.getFinalDamage(), submission.getCreatedAt());
	}
}
