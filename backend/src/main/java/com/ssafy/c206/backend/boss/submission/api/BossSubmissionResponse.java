package com.ssafy.c206.backend.boss.submission.api;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.ssafy.c206.backend.boss.domain.BossPhaseStatus;
import com.ssafy.c206.backend.boss.domain.BossRunStatus;
import com.ssafy.c206.backend.boss.submission.service.BossSubmissionResult;
import io.swagger.v3.oas.annotations.media.Schema;

public record BossSubmissionResponse(
		@Schema(description = "생성된 제출 식별자입니다.", example = "55") Long submissionId,
		@Schema(description = "제출한 보스전 식별자입니다.", example = "7") Long bossRunId,
		@Schema(description = "피해가 반영된 보스 페이즈 식별자입니다.", example = "21") Long bossPhaseId,
		@Schema(description = "제출 한도를 계산한 서비스 날짜입니다.", format = "date", example = "2026-07-27") LocalDate serviceDate,
		@Schema(description = "피해 계산 상세입니다.") Damage damage,
		@Schema(description = "피해 반영 후 페이즈 상태입니다.") Phase phase,
		@Schema(description = "피해 반영 후 보스전 상태입니다. SCHEDULED는 예정, ACTIVE는 진행, CLEARED는 클리어, ENDED_UNCLEARED는 미클리어 종료, CLOSED는 마감입니다.", allowableValues = {"SCHEDULED", "ACTIVE", "CLEARED", "ENDED_UNCLEARED", "CLOSED"}, example = "ACTIVE") BossRunStatus bossRunStatus) {
	public static BossSubmissionResponse from(BossSubmissionResult result) {
		return new BossSubmissionResponse(result.submissionId(), result.bossRunId(), result.bossPhaseId(), result.serviceDate(),
				new Damage(result.aiBaseScore(), result.wordBonus(), result.moodBonus(), result.weaknessBonus(),
						result.phaseModifier(), result.finalDamage()),
				new Phase(result.bossPhaseNo(), result.remainingHp(), result.phaseStatus()), result.bossRunStatus());
	}
	public record Damage(
			@Schema(description = "AI 평가 기반 점수입니다.", example = "39.00") BigDecimal aiBaseScore,
			@Schema(description = "오늘의 단어 보너스입니다.", example = "1.50") BigDecimal wordBonus,
			@Schema(description = "분위기 일치 보너스입니다.", example = "2.00") BigDecimal moodBonus,
			@Schema(description = "보스 약점 보너스입니다.", example = "3.00") BigDecimal weaknessBonus,
			@Schema(description = "페이즈 피해 배율입니다.", example = "1.25") BigDecimal phaseModifier,
			@Schema(description = "최종 반영 피해입니다.", minimum = "0", example = "57") long finalDamage) { }
	public record Phase(
			@Schema(description = "피해 반영 후 페이즈 번호입니다.", minimum = "1", example = "2") int phaseNo,
			@Schema(description = "피해 반영 후 남은 체력입니다.", minimum = "0", example = "563") long remainingHp,
			@Schema(description = "페이즈 상태입니다. WAITING은 대기, ACTIVE는 진행, CLEARED는 클리어입니다.", allowableValues = {"WAITING", "ACTIVE", "CLEARED"}, example = "ACTIVE") BossPhaseStatus status) { }
}
