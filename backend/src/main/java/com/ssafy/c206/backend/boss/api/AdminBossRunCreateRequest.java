package com.ssafy.c206.backend.boss.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;

public record AdminBossRunCreateRequest(
		@Schema(description = "대상 보스 ID") Long bossId,
		@Schema(description = "시작 시각", nullable = true) Instant startsAt,
		@Schema(description = "종료 시각") Instant endsAt,
		@Schema(description = "생성 즉시 활성화 여부") Boolean activate,
		@ArraySchema(arraySchema = @Schema(description = "최종 페이즈 목록"), minItems = 1)
		List<PhaseEntry> phases) {

	public record PhaseEntry(
			@Schema(description = "최대 HP. 생략하면 서버 기본값") Long maxHp,
			@Schema(description = "최소 인정 점수") BigDecimal hardMinScore,
			@Schema(description = "권장 점수") BigDecimal recommendedScore,
			@Schema(description = "피해 배율. 생략하면 1.0") BigDecimal damageMultiplier,
			@Schema(description = "페이즈 이미지 자산 ID") Long imageAssetId) {
	}
}
