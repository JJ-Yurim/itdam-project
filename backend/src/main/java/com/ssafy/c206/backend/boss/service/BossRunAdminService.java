package com.ssafy.c206.backend.boss.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import com.ssafy.c206.backend.boss.api.AdminBossPhaseImageUpdateRequest;
import com.ssafy.c206.backend.boss.api.AdminBossRunCreateRequest;
import com.ssafy.c206.backend.boss.api.AdminBossRunResponse;
import com.ssafy.c206.backend.boss.api.AdminBossRunActionRequest;
import com.ssafy.c206.backend.boss.api.AdminBossRunUpdateRequest;
import com.ssafy.c206.backend.boss.domain.Boss;
import com.ssafy.c206.backend.boss.domain.BossPhase;
import com.ssafy.c206.backend.boss.domain.BossRun;
import com.ssafy.c206.backend.boss.domain.BossRunStatus;
import com.ssafy.c206.backend.boss.domain.BossStatus;
import com.ssafy.c206.backend.boss.image.domain.ImageAsset;
import com.ssafy.c206.backend.boss.image.service.BossImageUrlResolver;
import com.ssafy.c206.backend.boss.repository.BossPhaseRepository;
import com.ssafy.c206.backend.boss.repository.BossRepository;
import com.ssafy.c206.backend.boss.repository.BossRunRepository;
import com.ssafy.c206.backend.common.api.DomainException;
import com.ssafy.c206.backend.common.api.ErrorCode;
import com.ssafy.c206.backend.common.api.PageResponse;
import com.ssafy.c206.backend.common.security.AuthenticatedUser;
import com.ssafy.c206.backend.common.security.AuthenticatedUsers;
import com.ssafy.c206.backend.common.time.ServiceClock;
import lombok.RequiredArgsConstructor;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 관리자 보스런 회차 생성·조회. MVP는 스케줄 자동 활성화 없이 생성 시 즉시 활성화 옵션만 지원한다.
 * 페이즈 HP를 생략하면 산정 원칙(BossRunHpPolicy) 기본값을 적용한다.
 */
@Service
@RequiredArgsConstructor
public class BossRunAdminService {

	private static final String ACTIVE_RUN_UNIQUE_INDEX = "uk_boss_runs_one_active_per_boss";
	private static final int MAX_PAGE_SIZE = 100;
	// boss_phases 점수 컬럼은 numeric(6,2), damage_multiplier는 numeric(6,3) — 초과 값은 저장 단계 예외 대신 검증으로 거부한다.

	private final BossRepository bossRepository;
	private final BossRunRepository bossRunRepository;
	private final BossPhaseRepository bossPhaseRepository;
	private final BossRunLifecycleService lifecycleService;
	private final BossRunHpPolicy hpPolicy;
	private final BossPhaseTemplateService templateService;
	private final BossImageUrlResolver imageUrlResolver;
	private final ServiceClock serviceClock;

	@Transactional
	public AdminBossRunResponse create(AuthenticatedUser principal, AdminBossRunCreateRequest request) {
		Instant now = serviceClock.now();
		validate(request, now);
		Boss boss = bossRepository.findByIdForUpdate(request.bossId())
				.orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND, "Boss not found: " + request.bossId()));
		if (boss.getStatus() != BossStatus.ACTIVE) {
			throw new DomainException(ErrorCode.VALIDATION_ERROR, "Boss is not active: " + request.bossId());
		}

		if (Boolean.TRUE.equals(request.activate())) {
			// 같은 보스의 ACTIVE 회차가 이미 있으면 사용자 제출 대상이 모호해지므로 중복 활성화를 막는다.
			if (bossRunRepository.existsByBossIdAndStatus(boss.getId(), BossRunStatus.ACTIVE)) {
				throw new DomainException(ErrorCode.CONFLICT, "An active run already exists for this boss.");
			}
		}
		List<BossPhaseTemplateService.ResolvedPhaseTemplate> resolvedPhases = templateService.validateForRun(
				AuthenticatedUsers.requireUserId(principal), boss.getId(), request.phases());
		BossRun run = BossRun.schedule(boss, request.startsAt(), request.endsAt(), now);
		for (BossPhaseTemplateService.ResolvedPhaseTemplate phase : resolvedPhases) {
			run.addPhase(phase.maxHp(), phase.hardMinScore(), phase.recommendedScore(), phase.damageMultiplier(),
					phase.imageAsset());
		}
		if (Boolean.TRUE.equals(request.activate())) run.activate(now);
		// saveAndFlush makes the database the final arbiter for concurrent ACTIVE-run creation.
		BossRun saved;
		try {
			saved = bossRunRepository.saveAndFlush(run);
		} catch (DataIntegrityViolationException exception) {
			if (causedByConstraint(exception, ACTIVE_RUN_UNIQUE_INDEX)) {
				throw new DomainException(ErrorCode.CONFLICT, "An active run already exists for this boss.");
			}
			throw exception;
		}
		templateService.replace(boss, resolvedPhases);
		templateService.activateTemporaryImages(resolvedPhases);
		return AdminBossRunResponse.from(saved, saved.getPhases(), imageUrlResolver);
	}

	@Transactional(readOnly = true)
	public PageResponse<AdminBossRunResponse> getRuns(String query, List<BossRunStatus> statuses,
			Instant from, Instant to, int page, int size) {
		if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
			throw new DomainException(ErrorCode.VALIDATION_ERROR, "page must be >= 0 and size must be between 1 and 100.");
		}
		if (from != null && to != null && from.isAfter(to)) {
			throw new DomainException(ErrorCode.VALIDATION_ERROR, "from must not be after to.");
		}
		Instant requestTime = serviceClock.now();
		if (bossRunRepository.existsPendingLifecycleTransition(requestTime)) {
			lifecycleService.syncAllRunStates(requestTime);
		}
		// boss는 findAllBy의 @EntityGraph로 함께 로드되고, phases는 배치 조회로 N+1을 피한다.
		List<BossRunStatus> requested = statuses == null ? List.of()
				: statuses.stream().filter(Objects::nonNull).distinct().toList();
		Collection<BossRunStatus> queryStatuses = requested.isEmpty()
				? EnumSet.allOf(BossRunStatus.class) : EnumSet.copyOf(requested);
		String escapedQuery = escapeLike(query);
		Page<BossRun> runs = bossRunRepository.searchAdmin(escapedQuery, escapedQuery != null, !requested.isEmpty(),
				queryStatuses, from, from != null, to, to != null,
				PageRequest.of(page, size, Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"))));
		List<Long> runIds = runs.getContent().stream().map(BossRun::getId).toList();
		Map<Long, List<BossPhase>> phasesByRunId = runIds.isEmpty() ? Map.of()
				: bossPhaseRepository.findDetailsByBossRunIdIn(runIds).stream()
						.collect(Collectors.groupingBy(phase -> phase.getBossRun().getId()));
		List<AdminBossRunResponse> responses = runs.getContent().stream()
				.map(run -> AdminBossRunResponse.from(run, phasesByRunId.getOrDefault(run.getId(), List.of()),
						imageUrlResolver))
				.toList();
		return new PageResponse<>(responses, runs.getNumber(), runs.getSize(), runs.getTotalElements(),
				runs.getTotalPages(), runs.hasNext());
	}

	private String escapeLike(String value) {
		if (value == null || value.isBlank()) return null;
		return value.trim().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
	}

	@Transactional
	public AdminBossRunResponse activate(AuthenticatedUser principal, Long runId, AdminBossRunActionRequest request) {
		AuthenticatedUsers.requireUserId(principal);
		BossRun run = lockedRun(runId, request.expectedUpdatedAt());
		if (bossRunRepository.existsByBossIdAndStatus(run.getBoss().getId(), BossRunStatus.ACTIVE)) {
			throw new DomainException(ErrorCode.CONFLICT, "An active run already exists for this boss.");
		}
		run.activate(serviceClock.now());
		try {
			bossRunRepository.saveAndFlush(run);
		} catch (DataIntegrityViolationException exception) {
			if (causedByConstraint(exception, ACTIVE_RUN_UNIQUE_INDEX)) {
				throw new DomainException(ErrorCode.CONFLICT, "An active run already exists for this boss.");
			}
			throw exception;
		}
		return response(run);
	}

	@Transactional
	public AdminBossRunResponse update(AuthenticatedUser principal, Long runId, AdminBossRunUpdateRequest request) {
		long userId = AuthenticatedUsers.requireUserId(principal);
		Instant now = serviceClock.now();
		BossRun run = lockedRun(runId, request.expectedUpdatedAt());
		if (run.getStatus() == BossRunStatus.ACTIVE) {
			if (request.bossId() != null || request.startsAt() != null || request.phases() != null) {
				throw new DomainException(ErrorCode.CONFLICT, "An active run can only change endsAt.");
			}
			if (request.endsAt() == null || !request.endsAt().isAfter(now)) {
				throw new DomainException(ErrorCode.VALIDATION_ERROR, "endsAt must be in the future.");
			}
			run.changeEndsAt(request.endsAt(), now);
			return response(run);
		}
		if (run.getStatus() != BossRunStatus.SCHEDULED) {
			throw new DomainException(ErrorCode.CONFLICT, "A terminal run is read-only.");
		}
		AdminBossRunCreateRequest candidate = new AdminBossRunCreateRequest(
				request.bossId(), request.startsAt(), request.endsAt(), false, request.phases());
		validate(candidate, now);
		Boss boss = bossRepository.findByIdForUpdate(request.bossId())
				.orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND, "Boss not found: " + request.bossId()));
		if (boss.getStatus() != BossStatus.ACTIVE) {
			throw new DomainException(ErrorCode.VALIDATION_ERROR, "Boss is not active: " + request.bossId());
		}
		List<BossPhaseTemplateService.ResolvedPhaseTemplate> resolved =
				templateService.validateForRun(userId, boss.getId(), request.phases());
		run.reschedule(boss, request.startsAt(), request.endsAt(), now);
		run.replaceScheduledPhases(now);
		bossRunRepository.flush();
		for (BossPhaseTemplateService.ResolvedPhaseTemplate phase : resolved) {
			run.addPhase(phase.maxHp(), phase.hardMinScore(), phase.recommendedScore(),
					phase.damageMultiplier(), phase.imageAsset());
		}
		templateService.activateTemporaryImages(resolved);
		return response(run);
	}

	@Transactional
	public AdminBossRunResponse changePhaseImage(AuthenticatedUser principal, Long runId, int phaseNo,
			AdminBossPhaseImageUpdateRequest request) {
		long userId = AuthenticatedUsers.requireUserId(principal);
		BossRun run = lockedRun(runId, request.expectedUpdatedAt());
		// 이미지 검증(400)보다 종료 상태 거절(409)을 먼저 판정한다.
		run.requirePhaseImageChangeable();
		ImageAsset image = templateService.resolveNewPhaseImage(userId, request.imageAssetId());
		run.changePhaseImage(phaseNo, image, serviceClock.now());
		image.activate();
		return response(run);
	}

	@Transactional
	public AdminBossRunResponse cancel(AuthenticatedUser principal, Long runId, AdminBossRunActionRequest request) {
		AuthenticatedUsers.requireUserId(principal);
		BossRun run = lockedRun(runId, request.expectedUpdatedAt());
		run.cancel(serviceClock.now());
		return response(run);
	}

	@Transactional
	public AdminBossRunResponse end(AuthenticatedUser principal, Long runId, AdminBossRunActionRequest request) {
		AuthenticatedUsers.requireUserId(principal);
		BossRun run = lockedRun(runId, request.expectedUpdatedAt());
		run.markEndedUncleared(serviceClock.now());
		return response(run);
	}

	private BossRun lockedRun(Long runId, Instant expectedUpdatedAt) {
		if (expectedUpdatedAt == null) throw new DomainException(ErrorCode.VALIDATION_ERROR, "expectedUpdatedAt is required.");
		// 만료만 맞추고 지연 활성화는 하지 않는다. 활성화는 updatedAt을 올리므로, 관리자가 목록에서
		// 본 예정 회차를 취소하려 할 때 여기서 활성화되면 expectedUpdatedAt 비교가 깨져 "다른
		// 관리자가 수정했다"는 엉뚱한 충돌로 막힌다. 목록 조회가 이미 상태를 맞춰 주므로,
		// 관리자는 자기가 본 상태 그대로 조작한다.
		lifecycleService.expireIfNeeded(runId, serviceClock.now());
		BossRun run = bossRunRepository.findWithBossByIdForUpdate(runId)
				.orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND, "Boss run not found: " + runId));
		if (!run.getUpdatedAt().equals(expectedUpdatedAt.truncatedTo(ChronoUnit.MICROS))) {
			throw new DomainException(ErrorCode.CONFLICT, "Boss run was modified by another administrator.");
		}
		return run;
	}

	private AdminBossRunResponse response(BossRun run) {
		return AdminBossRunResponse.from(run, bossPhaseRepository.findByBossRunIdOrderByPhaseNoAsc(run.getId()), imageUrlResolver);
	}

	private void validate(AdminBossRunCreateRequest request, Instant now) {
		if (request.bossId() == null) {
			throw new DomainException(ErrorCode.VALIDATION_ERROR, "bossId is required.");
		}
		if (request.endsAt() == null || !request.endsAt().isAfter(now)) {
			throw new DomainException(ErrorCode.VALIDATION_ERROR, "endsAt must be in the future.");
		}
		if (request.startsAt() != null && !request.endsAt().isAfter(request.startsAt())) {
			throw new DomainException(ErrorCode.VALIDATION_ERROR, "endsAt must be after startsAt.");
		}
		if (Boolean.TRUE.equals(request.activate()) && request.startsAt() != null && request.startsAt().isAfter(now)) {
			throw new DomainException(ErrorCode.VALIDATION_ERROR, "A run that starts in the future cannot be activated now.");
		}
		if (request.phases() == null || request.phases().isEmpty()) {
			throw new DomainException(ErrorCode.VALIDATION_ERROR, "At least one phase is required.");
		}
		for (AdminBossRunCreateRequest.PhaseEntry entry : request.phases()) {
			if (entry == null) {
				throw new DomainException(ErrorCode.VALIDATION_ERROR, "Phase entry must not be null.");
			}
			// 0·음수는 BossPhase 도메인도 거부하지만, 다른 필드와 같은 위치에서 일관되게 거부한다.
			long maxHp = entry.maxHp() != null ? entry.maxHp() : hpPolicy.defaultPhaseHp();
			BigDecimal multiplier = entry.damageMultiplier() != null ? entry.damageMultiplier() : BigDecimal.ONE;
			templateService.validatePhaseValues(maxHp, entry.hardMinScore(), entry.recommendedScore(), multiplier);
		}
	}

	private boolean causedByConstraint(Throwable exception, String constraintName) {
		Throwable current = exception;
		while (current != null) {
			if (current instanceof ConstraintViolationException constraintViolation
					&& constraintName.equalsIgnoreCase(constraintViolation.getConstraintName())) {
				return true;
			}
			current = current.getCause();
		}
		return false;
	}
}
