package com.ssafy.c206.backend.boss.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.ssafy.c206.backend.boss.api.AdminBossCreateRequest;
import com.ssafy.c206.backend.boss.api.AdminBossResponse;
import com.ssafy.c206.backend.boss.api.AdminBossPhaseTemplatesResponse;
import com.ssafy.c206.backend.boss.api.AdminBossBookCoverUpdateRequest;
import com.ssafy.c206.backend.boss.api.AdminBossUpdateRequest;
import com.ssafy.c206.backend.boss.api.AdminBossStatusUpdateRequest;
import com.ssafy.c206.backend.boss.api.AdminBossPhaseTemplatesUpdateRequest;
import com.ssafy.c206.backend.boss.api.BossPhaseTemplateResponse;
import com.ssafy.c206.backend.boss.domain.Boss;
import com.ssafy.c206.backend.boss.domain.BossMood;
import com.ssafy.c206.backend.boss.domain.BossPhaseTemplate;
import com.ssafy.c206.backend.boss.domain.BossStatus;
import com.ssafy.c206.backend.boss.image.domain.ImageAsset;
import com.ssafy.c206.backend.boss.image.service.BookCoverImageAssetService;
import com.ssafy.c206.backend.boss.image.service.BossImageUrlResolver;
import com.ssafy.c206.backend.boss.repository.BossMoodRepository;
import com.ssafy.c206.backend.boss.repository.BossRepository;
import com.ssafy.c206.backend.common.api.DomainException;
import com.ssafy.c206.backend.common.api.ErrorCode;
import com.ssafy.c206.backend.common.api.PageResponse;
import com.ssafy.c206.backend.common.security.AuthenticatedUser;
import com.ssafy.c206.backend.common.security.AuthenticatedUsers;
import com.ssafy.c206.backend.common.time.ServiceClock;
import com.ssafy.c206.backend.sentence.evaluation.domain.MoodCategory;
import com.ssafy.c206.backend.sentence.evaluation.repository.MoodCategoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 관리자 보스 원형 생성·조회. 무드 코드는 시딩된 MoodCategory(=MoodTaxonomy 10종)만 허용한다. */
@Service
@RequiredArgsConstructor
public class BossAdminService {

	private static final int MAX_PAGE_SIZE = 100;
	private static final int NAME_MAX_LENGTH = 150;
	// boss_moods.weight numeric(6,3) — 초과 값은 저장 단계 예외 대신 검증으로 거부한다.
	private static final BigDecimal MAX_WEIGHT = new BigDecimal("999.999");

	private final BossRepository bossRepository;
	private final BossMoodRepository bossMoodRepository;
	private final MoodCategoryRepository moodCategoryRepository;
	private final BossPhaseTemplateService templateService;
	private final BookCoverImageAssetService bookCoverImageAssetService;
	private final BossImageUrlResolver imageUrlResolver;
	private final ServiceClock serviceClock;

	@Transactional
	public AdminBossResponse create(AuthenticatedUser principal, AdminBossCreateRequest request) {
		validateName(request.name());
		long userId = AuthenticatedUsers.requireUserId(principal);
		ImageAsset bookCover = bookCoverImageAssetService.resolveAssignable(userId, request.bookCoverImageAssetId());
		List<ValidatedMood> validatedMoods = validateMoods(request.moods());
		List<BossPhaseTemplateService.ResolvedPhaseTemplate> resolvedPhases = templateService.validateInitial(
				userId, request.phaseTemplates());

		Boss boss = bossRepository.save(Boss.create(request.name(), request.description(), bookCover, serviceClock.now()));
		List<BossMood> moods = new ArrayList<>();
		for (ValidatedMood mood : validatedMoods) {
			moods.add(bossMoodRepository.save(BossMood.of(boss, mood.category(), mood.entry().role(), mood.entry().weight())));
		}
		List<BossPhaseTemplate> templates = templateService.replace(boss, resolvedPhases);
		templateService.activateTemporaryImages(resolvedPhases);
		bookCoverImageAssetService.activate(bookCover);
		return response(boss, moods, templateService.responses(templates));
	}

	@Transactional(readOnly = true)
	public PageResponse<AdminBossResponse> getBosses(String query, BossStatus status, int page, int size) {
		if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
			throw new DomainException(ErrorCode.VALIDATION_ERROR, "page must be >= 0 and size must be between 1 and 100.");
		}
		String normalizedQuery = query == null || query.isBlank() ? null : escapeLike(query.trim());
		Page<Boss> bosses = bossRepository.search(normalizedQuery, normalizedQuery != null, status, status != null,
				PageRequest.of(page, size, Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"))));
		List<Long> bossIds = bosses.getContent().stream().map(Boss::getId).toList();
		Map<Long, List<BossMood>> moodsByBossId = bossIds.isEmpty() ? Map.of()
				: bossMoodRepository.findDetailsByBossIdIn(bossIds).stream()
						.collect(Collectors.groupingBy(mood -> mood.getBoss().getId()));
		Map<Long, List<BossPhaseTemplateResponse>> phasesByBossId = templateService.responsesByBossIds(bossIds);
		List<AdminBossResponse> responses = bosses.getContent().stream()
				.map(boss -> response(boss, moodsByBossId.getOrDefault(boss.getId(), List.of()),
						phasesByBossId.getOrDefault(boss.getId(), List.of())))
				.toList();
		return new PageResponse<>(responses, bosses.getNumber(), bosses.getSize(), bosses.getTotalElements(),
				bosses.getTotalPages(), bosses.hasNext());
	}

	@Transactional
	public AdminBossResponse update(AuthenticatedUser principal, Long bossId, AdminBossUpdateRequest request) {
		AuthenticatedUsers.requireUserId(principal);
		validateName(request.name());
		List<ValidatedMood> validatedMoods = validateMoods(request.moods());
		Boss boss = lockedBoss(bossId, request.expectedUpdatedAt());
		boss.update(request.name(), request.description(), serviceClock.now());
		bossMoodRepository.deleteByBossId(bossId);
		List<BossMood> moods = validatedMoods.stream()
				.map(mood -> BossMood.of(boss, mood.category(), mood.entry().role(), mood.entry().weight()))
				.toList();
		bossMoodRepository.saveAll(moods);
		return response(boss, moods,
				templateService.responsesByBossIds(List.of(bossId)).getOrDefault(bossId, List.of()));
	}

	@Transactional
	public AdminBossResponse updatePhaseTemplates(AuthenticatedUser principal, Long bossId,
			AdminBossPhaseTemplatesUpdateRequest request) {
		long userId = AuthenticatedUsers.requireUserId(principal);
		Boss boss = lockedBoss(bossId, request.expectedUpdatedAt());
		List<BossPhaseTemplateService.ResolvedPhaseTemplate> resolved =
				templateService.validateReplacement(userId, request.phaseTemplates());
		List<BossPhaseTemplate> templates = templateService.replace(boss, resolved);
		templateService.activateTemporaryImages(resolved);
		boss.touch(serviceClock.now());
		return response(boss, bossMoodRepository.findDetailsByBossIdIn(List.of(bossId)),
				templateService.responses(templates));
	}

	@Transactional
	public AdminBossResponse updateStatus(AuthenticatedUser principal, Long bossId, AdminBossStatusUpdateRequest request) {
		AuthenticatedUsers.requireUserId(principal);
		if (request.status() == null) throw new DomainException(ErrorCode.VALIDATION_ERROR, "status is required.");
		Boss boss = lockedBoss(bossId, request.expectedUpdatedAt());
		if (request.status() == BossStatus.ACTIVE) boss.activate(serviceClock.now());
		else boss.deactivate(serviceClock.now());
		return response(boss, bossMoodRepository.findDetailsByBossIdIn(List.of(bossId)),
				templateService.responsesByBossIds(List.of(bossId)).getOrDefault(bossId, List.of()));
	}

	private Boss lockedBoss(Long bossId, Instant expectedUpdatedAt) {
		if (expectedUpdatedAt == null) {
			throw new DomainException(ErrorCode.VALIDATION_ERROR, "expectedUpdatedAt is required.");
		}
		Boss boss = bossRepository.findByIdForUpdate(bossId)
				.orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND, "Boss not found: " + bossId));
		Instant normalizedExpected = expectedUpdatedAt.truncatedTo(java.time.temporal.ChronoUnit.MICROS);
		if (!boss.getUpdatedAt().equals(normalizedExpected)) {
			throw new DomainException(ErrorCode.CONFLICT, "Boss was modified by another administrator.");
		}
		return boss;
	}

	private String escapeLike(String value) {
		return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
	}

	@Transactional(readOnly = true)
	public AdminBossPhaseTemplatesResponse getPhaseTemplates(Long bossId) {
		return templateService.getTemplates(bossId);
	}

	@Transactional
	public AdminBossResponse changeBookCover(AuthenticatedUser principal, Long bossId,
			AdminBossBookCoverUpdateRequest request) {
		long userId = AuthenticatedUsers.requireUserId(principal);
		Boss boss = bossRepository.findByIdForUpdate(bossId)
				.orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND, "Boss not found: " + bossId));
		ImageAsset cover = bookCoverImageAssetService.resolveAssignable(userId, request.imageAssetId());
		boss.changeBookCover(cover, serviceClock.now());
		bookCoverImageAssetService.activate(cover);
		List<BossMood> moods = bossMoodRepository.findDetailsByBossIdIn(List.of(bossId));
		return response(boss, moods,
				templateService.responsesByBossIds(List.of(bossId)).getOrDefault(bossId, List.of()));
	}

	private AdminBossResponse response(Boss boss, List<BossMood> moods,
			List<BossPhaseTemplateResponse> phases) {
		return AdminBossResponse.from(boss, moods, phases,
				imageUrlResolver.publicUrlOrNull(boss.getBookCoverImageAsset()));
	}

	/** null·공백은 Boss.create가 VALIDATION_ERROR로 거부하므로 저장 단계 예외가 되는 길이 초과만 확인한다. */
	private void validateName(String name) {
		if (name != null && name.trim().length() > NAME_MAX_LENGTH) {
			throw new DomainException(ErrorCode.VALIDATION_ERROR, "Boss name must be at most 150 characters.");
		}
	}

	private List<ValidatedMood> validateMoods(List<AdminBossCreateRequest.MoodEntry> entries) {
		if (entries == null || entries.isEmpty()) {
			throw new DomainException(ErrorCode.VALIDATION_ERROR, "At least one mood entry is required.");
		}
		List<AdminBossCreateRequest.MoodEntry> normalized = new ArrayList<>();
		Set<String> seen = new HashSet<>();
		Set<String> codes = new LinkedHashSet<>();
		for (AdminBossCreateRequest.MoodEntry entry : entries) {
			if (entry == null || entry.code() == null || entry.code().isBlank()) {
				throw new DomainException(ErrorCode.VALIDATION_ERROR, "Mood code is required.");
			}
			if (entry.role() == null) {
				throw new DomainException(ErrorCode.VALIDATION_ERROR, "Mood role is required.");
			}
			validateWeight(entry.weight());
			String code = entry.code().trim().toUpperCase(Locale.ROOT);
			if (!seen.add(code + ":" + entry.role())) {
				throw new DomainException(ErrorCode.VALIDATION_ERROR, "Duplicate mood entry: " + code + "/" + entry.role());
			}
			codes.add(code);
			normalized.add(new AdminBossCreateRequest.MoodEntry(code, entry.role(), entry.weight()));
		}

		Map<String, MoodCategory> categoriesByCode = moodCategoryRepository.findByCodeIn(codes).stream()
				.collect(Collectors.toMap(MoodCategory::getCode, Function.identity()));
		List<ValidatedMood> validated = new ArrayList<>();
		for (AdminBossCreateRequest.MoodEntry entry : normalized) {
			MoodCategory category = categoriesByCode.get(entry.code());
			if (category == null) {
				throw new DomainException(ErrorCode.VALIDATION_ERROR, "Unknown mood code: " + entry.code());
			}
			if (!category.isActive()) {
				throw new DomainException(ErrorCode.VALIDATION_ERROR, "Mood category is not active: " + entry.code());
			}
			validated.add(new ValidatedMood(entry, category));
		}
		return validated;
	}

	private void validateWeight(BigDecimal weight) {
		if (weight == null || weight.signum() <= 0) {
			throw new DomainException(ErrorCode.VALIDATION_ERROR, "Mood weight must be positive.");
		}
		if (weight.scale() > 3 || weight.compareTo(MAX_WEIGHT) > 0) {
			throw new DomainException(ErrorCode.VALIDATION_ERROR,
					"Mood weight must be at most 999.999 with up to 3 fraction digits.");
		}
	}

	private record ValidatedMood(AdminBossCreateRequest.MoodEntry entry, MoodCategory category) {
	}
}
