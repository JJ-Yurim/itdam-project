package com.ssafy.c206.backend.boss.api;

import java.time.Instant;
import java.util.List;

import com.ssafy.c206.backend.boss.domain.BossRun;
import com.ssafy.c206.backend.boss.domain.BossRunStatus;
import io.swagger.v3.oas.annotations.media.Schema;

public record BossRunResponse(
		@Schema(description = "보스전 식별자입니다.", example = "7") Long bossRunId,
		@Schema(description = "보스 식별자입니다.", example = "3") Long bossId,
		@Schema(description = "보스 이름입니다.", example = "새벽의 수호자") String bossName,
		@Schema(description = "보스전 설명입니다.", example = "새벽이 끝나기 전에 마음을 모으세요.") String bossDescription,
		@Schema(description = "보스전 상태입니다. SCHEDULED는 예정, ACTIVE는 진행, CLEARED는 클리어, ENDED_UNCLEARED는 미클리어 종료, CLOSED는 마감입니다.", allowableValues = {"SCHEDULED", "ACTIVE", "CLEARED", "ENDED_UNCLEARED", "CLOSED"}, example = "ACTIVE") BossRunStatus status,
		@Schema(description = "보스전 시작 시각입니다.", format = "date-time", example = "2026-07-27T01:00:00Z") Instant startedAt,
		@Schema(description = "보스전 종료 예정 시각입니다.", format = "date-time", example = "2026-07-28T01:00:00Z") Instant endsAt,
		@Schema(description = "클리어 시각입니다. 미클리어면 null입니다.", format = "date-time", nullable = true, example = "2026-07-27T12:00:00Z") Instant clearedAt,
		@Schema(description = "현재까지 누적 피해입니다.", minimum = "0", example = "1280") long accumulatedDamage,
		@Schema(description = "현재 보스 페이즈입니다.") BossPhaseResponse currentPhase,
		@Schema(description = "보스전 분위기와 약점입니다.") List<BossMoodResponse> moods,
		@Schema(description = "오늘 내가 이 보스전에 제출한 횟수입니다.", minimum = "0", example = "1") int mySubmissionCount,
		@Schema(description = "서비스 날짜별 남은 제출 가능 횟수입니다.", minimum = "0", example = "2") int remainingSubmissionCount,
		@Schema(description = "내 최근 제출 요약입니다.", example = "[]") List<BossSubmissionSummary> mySubmissions) {

	public static BossRunResponse from(BossRun run, BossPhaseResponse currentPhase, List<BossMoodResponse> moods,
			int mySubmissionCount, int remainingSubmissionCount, List<BossSubmissionSummary> mySubmissions) {
		return new BossRunResponse(run.getId(), run.getBoss().getId(), run.getBoss().getName(), run.getBoss().getDescription(),
				run.getStatus(), run.getStartedAt(), run.getEndsAt(), run.getClearedAt(), run.getAccumulatedDamage(), currentPhase,
				moods, mySubmissionCount, remainingSubmissionCount, mySubmissions);
	}
}
