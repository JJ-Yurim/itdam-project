package com.ssafy.c206.backend.boss.domain;

import java.math.BigDecimal;

import com.ssafy.c206.backend.boss.image.domain.ImageAsset;
import com.ssafy.c206.backend.common.api.DomainException;
import com.ssafy.c206.backend.common.api.ErrorCode;
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
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Positive;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "boss_phases", uniqueConstraints = @UniqueConstraint(
		name = "uk_boss_run_phase_no", columnNames = {"boss_run_id", "phase_no"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BossPhase {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "boss_run_id", nullable = false)
	private BossRun bossRun;

	@Positive
	@Column(name = "phase_no", nullable = false)
	private int phaseNo;

	@Positive
	@Column(name = "max_hp", nullable = false)
	private long maxHp;

	@Column(name = "remaining_hp", nullable = false)
	private long remainingHp;

	@Digits(integer = 4, fraction = 2)
	@Column(name = "hard_min_score", precision = 6, scale = 2)
	private BigDecimal hardMinScore;

	@Digits(integer = 4, fraction = 2)
	@Column(name = "recommended_score", precision = 6, scale = 2)
	private BigDecimal recommendedScore;

	@DecimalMin(value = "0.001")
	@Digits(integer = 3, fraction = 3)
	@Column(name = "damage_multiplier", nullable = false, precision = 6, scale = 3)
	private BigDecimal damageMultiplier;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "image_asset_id")
	private ImageAsset imageAsset;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 30)
	private BossPhaseStatus status;

	static BossPhase waiting(BossRun bossRun, int phaseNo, long maxHp, BigDecimal hardMinScore,
			BigDecimal recommendedScore, BigDecimal damageMultiplier) {
		return new BossPhase(bossRun, phaseNo, maxHp, hardMinScore, recommendedScore, damageMultiplier, null);
	}

	static BossPhase waiting(BossRun bossRun, int phaseNo, long maxHp, BigDecimal hardMinScore,
			BigDecimal recommendedScore, BigDecimal damageMultiplier, ImageAsset imageAsset) {
		if (imageAsset == null) {
			throw new DomainException(ErrorCode.VALIDATION_ERROR, "imageAsset is required.");
		}
		return new BossPhase(bossRun, phaseNo, maxHp, hardMinScore, recommendedScore, damageMultiplier,
				imageAsset);
	}

	private BossPhase(BossRun bossRun, int phaseNo, long maxHp, BigDecimal hardMinScore,
			BigDecimal recommendedScore, BigDecimal damageMultiplier, ImageAsset imageAsset) {
		if (bossRun == null || phaseNo <= 0) {
			throw new DomainException(ErrorCode.VALIDATION_ERROR, "boss run and positive phase number are required.");
		}
		this.bossRun = bossRun;
		this.phaseNo = phaseNo;
		this.maxHp = requirePositive(maxHp, "maxHp");
		this.remainingHp = maxHp;
		this.hardMinScore = requireNonNegative(hardMinScore, "hardMinScore");
		this.recommendedScore = requireNonNegative(recommendedScore, "recommendedScore");
		this.damageMultiplier = requirePositive(damageMultiplier, "damageMultiplier");
		this.imageAsset = imageAsset;
		this.status = BossPhaseStatus.WAITING;
	}

	/** 전투 진행 값(HP·상태)은 그대로 두고 이미지 참조만 교체한다. */
	void changeImage(ImageAsset imageAsset) {
		if (imageAsset == null) {
			throw new DomainException(ErrorCode.VALIDATION_ERROR, "imageAsset is required.");
		}
		this.imageAsset = imageAsset;
	}

	void activate() {
		if (status != BossPhaseStatus.WAITING) {
			throw new DomainException(ErrorCode.CONFLICT, "Only a waiting boss phase can be activated.");
		}
		status = BossPhaseStatus.ACTIVE;
	}

	void clear() {
		if (status != BossPhaseStatus.ACTIVE) {
			throw new DomainException(ErrorCode.CONFLICT, "Only an active boss phase can be cleared.");
		}
		remainingHp = 0;
		status = BossPhaseStatus.CLEARED;
	}

	/** Applies an already calculated, non-negative damage snapshot to the active phase. */
	public boolean applyDamage(long damage) {
		if (status != BossPhaseStatus.ACTIVE) {
			throw new DomainException(ErrorCode.CONFLICT, "Only an active boss phase can receive damage.");
		}
		if (damage < 0) {
			throw new DomainException(ErrorCode.VALIDATION_ERROR, "damage must not be negative.");
		}
		if (damage >= remainingHp) {
			clear();
			return true;
		}
		remainingHp -= damage;
		return false;
	}

	private static long requirePositive(long value, String field) {
		if (value <= 0) {
			throw new DomainException(ErrorCode.VALIDATION_ERROR, field + " must be positive.");
		}
		return value;
	}

	private static BigDecimal requirePositive(BigDecimal value, String field) {
		if (value == null || value.signum() <= 0) {
			throw new DomainException(ErrorCode.VALIDATION_ERROR, field + " must be positive.");
		}
		return value;
	}

	private static BigDecimal requireNonNegative(BigDecimal value, String field) {
		if (value != null && value.signum() < 0) {
			throw new DomainException(ErrorCode.VALIDATION_ERROR, field + " must not be negative.");
		}
		return value;
	}
}
