package com.ssafy.c206.backend.boss.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import com.ssafy.c206.backend.boss.image.domain.ImageAsset;
import com.ssafy.c206.backend.common.api.DomainException;
import com.ssafy.c206.backend.common.api.ErrorCode;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Index;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "boss_runs", indexes = @Index(name = "idx_boss_runs_status_ends_at", columnList = "status,ends_at"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BossRun {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "boss_id", nullable = false)
	private Boss boss;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 30)
	private BossRunStatus status;

	@Column(name = "current_phase_no", nullable = false)
	private int currentPhaseNo;

	@Column(name = "accumulated_damage", nullable = false)
	private long accumulatedDamage;

	@Column(name = "started_at")
	private Instant startedAt;

	@Column(name = "ends_at")
	private Instant endsAt;

	@Column(name = "cleared_at")
	private Instant clearedAt;

	@Column(nullable = false, updatable = false)
	private Instant createdAt;

	@Column(nullable = false)
	private Instant updatedAt;

	@OneToMany(mappedBy = "bossRun", cascade = CascadeType.ALL, orphanRemoval = true)
	@OrderBy("phaseNo ASC")
	private List<BossPhase> phases = new ArrayList<>();

	private BossRun(Boss boss, Instant startsAt, Instant endsAt, Instant now) {
		if (boss == null || now == null) {
			throw new DomainException(ErrorCode.VALIDATION_ERROR, "boss and created time are required.");
		}
		if (startsAt != null && endsAt != null && !endsAt.isAfter(startsAt)) {
			throw new DomainException(ErrorCode.VALIDATION_ERROR, "endsAt must be after startsAt.");
		}
		now = requireTime(now);
		this.boss = boss;
		this.status = BossRunStatus.SCHEDULED;
		this.currentPhaseNo = 1;
		this.accumulatedDamage = 0;
		this.startedAt = startsAt;
		this.endsAt = endsAt;
		this.createdAt = now;
		this.updatedAt = now;
	}

	public static BossRun schedule(Boss boss, Instant startsAt, Instant endsAt, Instant now) {
		return new BossRun(boss, startsAt, endsAt, now);
	}

	/**
	 * Legacy fixture path while image_asset_id remains nullable for existing data.
	 * Remove after operational backfill and the follow-up NOT NULL migration.
	 */
	@Deprecated(forRemoval = true)
	public BossPhase addPhase(long maxHp, BigDecimal hardMinScore, BigDecimal recommendedScore,
			BigDecimal damageMultiplier) {
		if (status != BossRunStatus.SCHEDULED) {
			throw new DomainException(ErrorCode.CONFLICT, "Boss phases can only be initialized while scheduled.");
		}
		BossPhase phase = BossPhase.waiting(this, phases.size() + 1, maxHp, hardMinScore, recommendedScore,
				damageMultiplier);
		phases.add(phase);
		return phase;
	}

	public BossPhase addPhase(long maxHp, BigDecimal hardMinScore, BigDecimal recommendedScore,
			BigDecimal damageMultiplier, ImageAsset imageAsset) {
		if (status != BossRunStatus.SCHEDULED) {
			throw new DomainException(ErrorCode.CONFLICT, "Boss phases can only be initialized while scheduled.");
		}
		BossPhase phase = BossPhase.waiting(this, phases.size() + 1, maxHp, hardMinScore, recommendedScore,
				damageMultiplier, imageAsset);
		phases.add(phase);
		return phase;
	}

	public void activate(Instant now) {
		if (status != BossRunStatus.SCHEDULED) {
			throw new DomainException(ErrorCode.CONFLICT, "Only a scheduled boss run can be activated.");
		}
		BossPhase firstPhase = phase(currentPhaseNo);
		firstPhase.activate();
		status = BossRunStatus.ACTIVE;
		if (startedAt == null) {
			startedAt = requireTime(now);
		}
		updatedAt = requireTime(now);
	}

	public void markEndedUncleared(Instant now) {
		if (status != BossRunStatus.ACTIVE) {
			throw new DomainException(ErrorCode.CONFLICT, "Only an active boss run can end uncleared.");
		}
		status = BossRunStatus.ENDED_UNCLEARED;
		updatedAt = requireTime(now);
	}

	public void cancel(Instant now) {
		if (status != BossRunStatus.SCHEDULED) {
			throw new DomainException(ErrorCode.CONFLICT, "Only a scheduled boss run can be cancelled.");
		}
		status = BossRunStatus.CANCELLED;
		updatedAt = requireTime(now);
	}

	public void reschedule(Boss boss, Instant startsAt, Instant endsAt, Instant now) {
		if (status != BossRunStatus.SCHEDULED) {
			throw new DomainException(ErrorCode.CONFLICT, "Only a scheduled boss run can be fully updated.");
		}
		if (boss == null || endsAt == null || (startsAt != null && !endsAt.isAfter(startsAt))) {
			throw new DomainException(ErrorCode.VALIDATION_ERROR, "Valid boss and schedule are required.");
		}
		this.boss = boss;
		this.startedAt = startsAt;
		this.endsAt = endsAt;
		this.updatedAt = requireTime(now);
	}

	public void replaceScheduledPhases(Instant now) {
		if (status != BossRunStatus.SCHEDULED) {
			throw new DomainException(ErrorCode.CONFLICT, "Only a scheduled boss run can replace phases.");
		}
		phases.clear();
		currentPhaseNo = 1;
		accumulatedDamage = 0;
		updatedAt = requireTime(now);
	}

	/** 예정·진행 중 회차에서 지정한 페이즈의 이미지 참조만 교체한다. 종료 상태는 스냅샷 보존을 위해 거부한다. */
	public void changePhaseImage(int phaseNo, ImageAsset imageAsset, Instant now) {
		requirePhaseImageChangeable();
		BossPhase target = phases.stream().filter(phase -> phase.getPhaseNo() == phaseNo).findFirst()
				.orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND, "Boss phase not found: " + phaseNo));
		target.changeImage(imageAsset);
		updatedAt = requireTime(now);
	}

	public void requirePhaseImageChangeable() {
		if (status != BossRunStatus.SCHEDULED && status != BossRunStatus.ACTIVE) {
			throw new DomainException(ErrorCode.CONFLICT, "Only a scheduled or active boss run can change phase images.");
		}
	}

	public void changeEndsAt(Instant endsAt, Instant now) {
		if (status != BossRunStatus.ACTIVE) {
			throw new DomainException(ErrorCode.CONFLICT, "Only an active boss run can change endsAt.");
		}
		if (endsAt == null || (startedAt != null && !endsAt.isAfter(startedAt))) {
			throw new DomainException(ErrorCode.VALIDATION_ERROR, "endsAt must be after startedAt.");
		}
		this.endsAt = endsAt;
		this.updatedAt = requireTime(now);
	}

	public void markCleared(Instant now) {
		if (status != BossRunStatus.ACTIVE) {
			throw new DomainException(ErrorCode.CONFLICT, "Only an active boss run can be cleared.");
		}
		status = BossRunStatus.CLEARED;
		clearedAt = requireTime(now);
		updatedAt = now;
	}

	/** Advances exactly once after the currently active phase reaches zero HP. */
	public void advanceAfterCurrentPhaseClear(BossPhase clearedPhase, Instant now, BossPhase nextPhase) {
		if (status != BossRunStatus.ACTIVE) {
			throw new DomainException(ErrorCode.CONFLICT, "Only an active boss run can advance phases.");
		}
		if (clearedPhase == null || clearedPhase.getPhaseNo() != currentPhaseNo
				|| clearedPhase.getStatus() != BossPhaseStatus.CLEARED) {
			throw new DomainException(ErrorCode.CONFLICT, "The current phase has not been cleared.");
		}
		int nextPhaseNo = currentPhaseNo + 1;
		if (nextPhase == null) {
			markCleared(now);
			return;
		}
		if (nextPhase.getPhaseNo() != nextPhaseNo) {
			throw new DomainException(ErrorCode.CONFLICT, "The next boss phase is invalid.");
		}
		nextPhase.activate();
		currentPhaseNo = nextPhaseNo;
		updatedAt = requireTime(now);
	}

	public void addAccumulatedDamage(long damage, Instant now) {
		if (damage < 0) {
			throw new DomainException(ErrorCode.VALIDATION_ERROR, "damage must not be negative.");
		}
		try {
			accumulatedDamage = Math.addExact(accumulatedDamage, damage);
		} catch (ArithmeticException exception) {
			throw new DomainException(ErrorCode.CONFLICT, "Accumulated damage overflow.");
		}
		updatedAt = requireTime(now);
	}

	public void close(Instant now) {
		if (status != BossRunStatus.CLEARED && status != BossRunStatus.ENDED_UNCLEARED) {
			throw new DomainException(ErrorCode.CONFLICT, "Only a completed boss run can be closed.");
		}
		status = BossRunStatus.CLOSED;
		updatedAt = requireTime(now);
	}

	public List<BossPhase> getPhases() {
		return List.copyOf(phases);
	}

	private BossPhase phase(int phaseNo) {
		return phases.stream().min(Comparator.comparingInt(BossPhase::getPhaseNo))
				.filter(phase -> phase.getPhaseNo() == phaseNo)
				.orElseThrow(() -> new DomainException(ErrorCode.CONFLICT,
						"A boss run needs a current phase before activation."));
	}

	private static Instant requireTime(Instant value) {
		if (value == null) {
			throw new DomainException(ErrorCode.VALIDATION_ERROR, "time is required.");
		}
		return value.truncatedTo(ChronoUnit.MICROS);
	}
}
