package com.ssafy.c206.backend.boss.api;

import java.time.Instant;
import java.util.List;

import com.ssafy.c206.backend.boss.domain.Boss;
import com.ssafy.c206.backend.boss.domain.BossMood;
import com.ssafy.c206.backend.boss.domain.BossStatus;

public record AdminBossResponse(
		Long id,
		String name,
		String description,
		BossStatus status,
		Long bookCoverImageAssetId,
		String bookCoverImageUrl,
		List<BossMoodResponse> moods,
		List<BossPhaseTemplateResponse> phaseTemplates,
		Instant createdAt,
		Instant updatedAt) {

	public static AdminBossResponse from(Boss boss, List<BossMood> moods,
			List<BossPhaseTemplateResponse> phaseTemplates, String bookCoverImageUrl) {
		return new AdminBossResponse(boss.getId(), boss.getName(), boss.getDescription(), boss.getStatus(),
				boss.getBookCoverImageAsset() == null ? null : boss.getBookCoverImageAsset().getId(), bookCoverImageUrl,
				moods.stream().map(BossMoodResponse::from).toList(), phaseTemplates, boss.getCreatedAt(), boss.getUpdatedAt());
	}
}
