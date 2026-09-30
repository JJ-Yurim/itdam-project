package com.ssafy.c206.backend.boss.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;

import com.ssafy.c206.backend.boss.domain.BossPhase;
import com.ssafy.c206.backend.boss.domain.BossPhaseStatus;
import com.ssafy.c206.backend.boss.domain.BossRun;
import com.ssafy.c206.backend.boss.domain.BossRunStatus;
import com.ssafy.c206.backend.boss.image.service.BossImageUrlResolver;

public record AdminBossRunResponse(
		Long id, Long bossId, String bossName, BossRunStatus status, int currentPhaseNo,
		Instant startedAt, Instant endsAt, List<PhaseResponse> phases, Instant createdAt, Instant updatedAt) {

	public record PhaseResponse(
			int phaseNo, long maxHp, long remainingHp,
			BigDecimal hardMinScore, BigDecimal recommendedScore, BigDecimal damageMultiplier,
			Long imageAssetId, String imageUrl, BossPhaseStatus status) {

		static PhaseResponse from(BossPhase phase, BossImageUrlResolver resolver) {
			return new PhaseResponse(phase.getPhaseNo(), phase.getMaxHp(), phase.getRemainingHp(),
					phase.getHardMinScore(), phase.getRecommendedScore(), phase.getDamageMultiplier(),
					phase.getImageAsset() == null ? null : phase.getImageAsset().getId(),
					resolver.publicUrl(phase.getImageAsset()), phase.getStatus());
		}
	}

	public static AdminBossRunResponse from(BossRun run, List<BossPhase> phases, BossImageUrlResolver resolver) {
		return new AdminBossRunResponse(run.getId(), run.getBoss().getId(), run.getBoss().getName(), run.getStatus(),
				run.getCurrentPhaseNo(), run.getStartedAt(), run.getEndsAt(),
				phases.stream().sorted(Comparator.comparingInt(BossPhase::getPhaseNo))
						.map(phase -> PhaseResponse.from(phase, resolver)).toList(), run.getCreatedAt(), run.getUpdatedAt());
	}
}
