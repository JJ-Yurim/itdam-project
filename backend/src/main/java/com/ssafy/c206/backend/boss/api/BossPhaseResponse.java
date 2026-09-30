package com.ssafy.c206.backend.boss.api;

import java.math.BigDecimal;
import java.math.RoundingMode;

import com.ssafy.c206.backend.boss.domain.BossPhase;
import com.ssafy.c206.backend.boss.domain.BossPhaseStatus;
import com.ssafy.c206.backend.boss.image.service.BossImageUrlResolver;
import io.swagger.v3.oas.annotations.media.Schema;

public record BossPhaseResponse(
		Long phaseId,
		int phaseNo,
		long maxHp,
		long remainingHp,
		BigDecimal progressPercent,
		BigDecimal hardMinScore,
		BigDecimal recommendedScore,
		BigDecimal damageMultiplier,
		@Schema(description = "현재 페이즈 이미지의 공개 URL입니다.") String imageUrl,
		BossPhaseStatus status) {

	public static BossPhaseResponse from(BossPhase phase, BossImageUrlResolver resolver) {
		BigDecimal progress = BigDecimal.valueOf(phase.getMaxHp() - phase.getRemainingHp())
				.multiply(BigDecimal.valueOf(100))
				.divide(BigDecimal.valueOf(phase.getMaxHp()), 2, RoundingMode.HALF_UP);
		return new BossPhaseResponse(phase.getId(), phase.getPhaseNo(), phase.getMaxHp(), phase.getRemainingHp(), progress,
				phase.getHardMinScore(), phase.getRecommendedScore(), phase.getDamageMultiplier(),
				resolver.publicUrl(phase.getImageAsset()), phase.getStatus());
	}
}
