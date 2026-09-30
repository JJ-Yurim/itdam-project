package com.ssafy.c206.backend.boss.api;

import java.math.BigDecimal;

import com.ssafy.c206.backend.boss.domain.BossPhaseTemplate;
import com.ssafy.c206.backend.boss.image.service.BossImageUrlResolver;

public record BossPhaseTemplateResponse(
		Long id, int phaseNo, long maxHp,
		BigDecimal hardMinScore, BigDecimal recommendedScore, BigDecimal damageMultiplier,
		Long imageAssetId, String imageUrl) {

	public static BossPhaseTemplateResponse from(BossPhaseTemplate template, BossImageUrlResolver resolver) {
		return new BossPhaseTemplateResponse(template.getId(), template.getPhaseNo(), template.getMaxHp(),
				template.getHardMinScore(), template.getRecommendedScore(), template.getDamageMultiplier(),
				template.getImageAsset().getId(), resolver.publicUrl(template.getImageAsset()));
	}
}
