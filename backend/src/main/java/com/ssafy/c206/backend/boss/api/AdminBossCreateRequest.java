package com.ssafy.c206.backend.boss.api;

import java.math.BigDecimal;
import java.util.List;

import com.ssafy.c206.backend.boss.domain.MoodRole;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;

public record AdminBossCreateRequest(
		@Schema(description = "보스 이름입니다.", maxLength = 150) String name,
		@Schema(description = "보스 설명입니다.") String description,
		@Schema(description = "완성된 책에 사용할 기본 표지 이미지 자산 ID", requiredMode = Schema.RequiredMode.REQUIRED)
		Long bookCoverImageAssetId,
		@ArraySchema(arraySchema = @Schema(description = "보스의 분위기 구성입니다."), minItems = 1)
		List<MoodEntry> moods,
		@ArraySchema(arraySchema = @Schema(description = "보스런 생성 시 재사용할 페이즈 기본값입니다."), minItems = 1)
		List<PhaseTemplateEntry> phaseTemplates) {

	public record MoodEntry(
			@Schema(description = "분위기 코드입니다.") String code,
			@Schema(description = "분위기 역할입니다.") MoodRole role,
			@Schema(description = "피해 계산 가중치입니다.") BigDecimal weight) {
	}

	public record PhaseTemplateEntry(
			@Schema(description = "페이즈 최대 HP입니다. 생략하면 서버 기본값을 사용합니다.") Long maxHp,
			@Schema(description = "최소 인정 점수입니다.") BigDecimal hardMinScore,
			@Schema(description = "권장 점수입니다.") BigDecimal recommendedScore,
			@Schema(description = "피해 배율입니다. 생략하면 1.0입니다.") BigDecimal damageMultiplier,
			@Schema(description = "먼저 업로드한 이미지 자산 ID입니다.") Long imageAssetId) {
	}
}
