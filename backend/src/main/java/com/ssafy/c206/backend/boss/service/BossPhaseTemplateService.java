package com.ssafy.c206.backend.boss.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.ssafy.c206.backend.boss.api.AdminBossCreateRequest;
import com.ssafy.c206.backend.boss.api.AdminBossPhaseTemplatesResponse;
import com.ssafy.c206.backend.boss.api.AdminBossRunCreateRequest;
import com.ssafy.c206.backend.boss.api.BossPhaseTemplateResponse;
import com.ssafy.c206.backend.boss.domain.Boss;
import com.ssafy.c206.backend.boss.domain.BossPhaseTemplate;
import com.ssafy.c206.backend.boss.image.domain.ImageAsset;
import com.ssafy.c206.backend.boss.image.domain.ImageAssetStatus;
import com.ssafy.c206.backend.boss.image.repository.ImageAssetRepository;
import com.ssafy.c206.backend.boss.image.service.BossImageUrlResolver;
import com.ssafy.c206.backend.boss.repository.BossPhaseTemplateRepository;
import com.ssafy.c206.backend.boss.repository.BossRepository;
import com.ssafy.c206.backend.common.api.DomainException;
import com.ssafy.c206.backend.common.api.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class BossPhaseTemplateService {

	private static final BigDecimal MAX_SCORE = new BigDecimal("9999.99");
	private static final BigDecimal MAX_MULTIPLIER = new BigDecimal("999.999");

	private final BossRepository bossRepository;
	private final BossPhaseTemplateRepository templateRepository;
	private final ImageAssetRepository imageAssetRepository;
	private final BossImageUrlResolver imageUrlResolver;
	private final BossRunHpPolicy hpPolicy;

	public List<ResolvedPhaseTemplate> validateInitial(long userId,
			List<AdminBossCreateRequest.PhaseTemplateEntry> entries) {
		if (entries == null || entries.isEmpty()) {
			throw validation("At least one phase template is required.");
		}
		List<ResolvedPhaseTemplate> resolved = new ArrayList<>();
		Set<Long> imageIds = new HashSet<>();
		for (int index = 0; index < entries.size(); index++) {
			AdminBossCreateRequest.PhaseTemplateEntry entry = entries.get(index);
			if (entry == null || entry.imageAssetId() == null) {
				throw validation("Every phase template requires an imageAssetId.");
			}
			if (!imageIds.add(entry.imageAssetId())) {
				throw validation("The same image cannot be used twice in one phase list.");
			}
			long maxHp = entry.maxHp() != null ? entry.maxHp() : hpPolicy.defaultPhaseHp();
			BigDecimal multiplier = entry.damageMultiplier() != null ? entry.damageMultiplier() : BigDecimal.ONE;
			validatePhaseValues(maxHp, entry.hardMinScore(), entry.recommendedScore(), multiplier);
			ImageAsset image = imageAssetRepository.findByIdForUpdate(entry.imageAssetId())
					.orElseThrow(() -> validation("Unknown imageAssetId: " + entry.imageAssetId()));
			if (!image.isBossPhase()) {
				throw validation("Every phase image must be uploaded as a boss phase image.");
			}
			if (!image.isTemporaryOwnedBy(userId)) {
				throw validation("Initial phase images must belong to the current administrator.");
			}
			resolved.add(new ResolvedPhaseTemplate(index + 1, maxHp, entry.hardMinScore(),
					entry.recommendedScore(), multiplier, image));
		}
		return List.copyOf(resolved);
	}

	public List<ResolvedPhaseTemplate> validateReplacement(long userId,
			List<AdminBossCreateRequest.PhaseTemplateEntry> entries) {
		return validateInitial(userId, entries);
	}

	public List<ResolvedPhaseTemplate> validateForRun(long userId, Long bossId,
			List<AdminBossRunCreateRequest.PhaseEntry> entries) {
		if (entries == null || entries.isEmpty()) {
			throw validation("At least one phase is required.");
		}
		List<ResolvedPhaseTemplate> resolved = new ArrayList<>();
		Set<Long> imageIds = new HashSet<>();
		for (int index = 0; index < entries.size(); index++) {
			AdminBossRunCreateRequest.PhaseEntry entry = entries.get(index);
			if (entry == null || entry.imageAssetId() == null) {
				throw validation("Every phase requires an imageAssetId.");
			}
			if (!imageIds.add(entry.imageAssetId())) {
				throw validation("The same image cannot be used twice in one phase list.");
			}
			long maxHp = entry.maxHp() != null ? entry.maxHp() : hpPolicy.defaultPhaseHp();
			BigDecimal multiplier = entry.damageMultiplier() != null ? entry.damageMultiplier() : BigDecimal.ONE;
			validatePhaseValues(maxHp, entry.hardMinScore(), entry.recommendedScore(), multiplier);
			ImageAsset image = imageAssetRepository.findByIdForUpdate(entry.imageAssetId())
					.orElseThrow(() -> validation("Unknown imageAssetId: " + entry.imageAssetId()));
			if (!image.isBossPhase()) {
				throw validation("Every phase image must be uploaded as a boss phase image.");
			}
			boolean allowedTemporary = image.isTemporaryOwnedBy(userId);
			boolean allowedActive = image.getStatus() == ImageAssetStatus.ACTIVE
					&& templateRepository.existsByBossIdAndImageAssetId(bossId, image.getId());
			if (!allowedTemporary && !allowedActive) {
				throw validation("An image must be newly uploaded by the administrator or already used by this boss.");
			}
			resolved.add(new ResolvedPhaseTemplate(index + 1, maxHp, entry.hardMinScore(),
					entry.recommendedScore(), multiplier, image));
		}
		return List.copyOf(resolved);
	}

	/** 진행 중 교체용 새 이미지는 재사용을 허용하지 않고 관리자 본인의 TEMPORARY 업로드만 받는다. */
	public ImageAsset resolveNewPhaseImage(long userId, Long imageAssetId) {
		if (imageAssetId == null) {
			throw validation("imageAssetId is required.");
		}
		ImageAsset image = imageAssetRepository.findByIdForUpdate(imageAssetId)
				.orElseThrow(() -> validation("Unknown imageAssetId: " + imageAssetId));
		if (!image.isBossPhase()) {
			throw validation("Every phase image must be uploaded as a boss phase image.");
		}
		if (!image.isTemporaryOwnedBy(userId)) {
			throw validation("A replacement phase image must be a temporary upload owned by the current administrator.");
		}
		return image;
	}

	public void validatePhaseValues(long maxHp, BigDecimal hardMinScore, BigDecimal recommendedScore,
			BigDecimal damageMultiplier) {
		if (maxHp <= 0) throw validation("maxHp must be positive.");
		validateScore(hardMinScore, "hardMinScore");
		validateScore(recommendedScore, "recommendedScore");
		if (hardMinScore != null && recommendedScore != null
				&& recommendedScore.compareTo(hardMinScore) < 0) {
			throw validation("recommendedScore must be greater than or equal to hardMinScore.");
		}
		if (damageMultiplier == null || damageMultiplier.signum() <= 0 || damageMultiplier.scale() > 3
				|| damageMultiplier.compareTo(MAX_MULTIPLIER) > 0) {
			throw validation("damageMultiplier must be at most 999.999 with up to 3 fraction digits.");
		}
	}

	public List<BossPhaseTemplate> replace(Boss boss, List<ResolvedPhaseTemplate> phases) {
		templateRepository.deleteByBossId(boss.getId());
		templateRepository.flush();
		List<BossPhaseTemplate> templates = phases.stream()
				.map(phase -> BossPhaseTemplate.of(boss, phase.phaseNo(), phase.maxHp(), phase.hardMinScore(),
						phase.recommendedScore(), phase.damageMultiplier(), phase.imageAsset()))
				.toList();
		return templateRepository.saveAll(templates);
	}

	public void activateTemporaryImages(List<ResolvedPhaseTemplate> phases) {
		phases.forEach(phase -> phase.imageAsset().activate());
	}

	@Transactional(readOnly = true)
	public AdminBossPhaseTemplatesResponse getTemplates(Long bossId) {
		Boss boss = bossRepository.findById(bossId)
				.orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND, "Boss not found: " + bossId));
		return new AdminBossPhaseTemplatesResponse(boss.getId(), boss.getName(),
				responses(templateRepository.findByBossIdOrderByPhaseNo(bossId)));
	}

	public List<BossPhaseTemplateResponse> responses(List<BossPhaseTemplate> templates) {
		return templates.stream().map(template -> BossPhaseTemplateResponse.from(template, imageUrlResolver)).toList();
	}

	public Map<Long, List<BossPhaseTemplateResponse>> responsesByBossIds(List<Long> bossIds) {
		if (bossIds.isEmpty()) return Map.of();
		return templateRepository.findDetailsByBossIdIn(bossIds).stream()
				.collect(Collectors.groupingBy(template -> template.getBoss().getId(),
						Collectors.mapping(template -> BossPhaseTemplateResponse.from(template, imageUrlResolver),
								Collectors.toList())));
	}

	private void validateScore(BigDecimal score, String field) {
		if (score != null && (score.signum() < 0 || score.scale() > 2 || score.compareTo(MAX_SCORE) > 0)) {
			throw validation(field + " must be between 0 and 9999.99 with up to 2 fraction digits.");
		}
	}

	private DomainException validation(String message) {
		return new DomainException(ErrorCode.VALIDATION_ERROR, message);
	}

	public record ResolvedPhaseTemplate(int phaseNo, long maxHp, BigDecimal hardMinScore,
			BigDecimal recommendedScore, BigDecimal damageMultiplier, ImageAsset imageAsset) {
	}
}
