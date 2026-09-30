package com.ssafy.c206.backend.boss.domain;

import java.math.BigDecimal;

import com.ssafy.c206.backend.boss.image.domain.ImageAsset;
import com.ssafy.c206.backend.common.api.DomainException;
import com.ssafy.c206.backend.common.api.ErrorCode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "boss_phase_templates", uniqueConstraints = @UniqueConstraint(
		name = "uk_boss_phase_templates_boss_phase", columnNames = {"boss_id", "phase_no"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BossPhaseTemplate {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "boss_id", nullable = false)
	private Boss boss;

	@Column(name = "phase_no", nullable = false)
	private int phaseNo;

	@Column(name = "max_hp", nullable = false)
	private long maxHp;

	@Column(name = "hard_min_score", precision = 6, scale = 2)
	private BigDecimal hardMinScore;

	@Column(name = "recommended_score", precision = 6, scale = 2)
	private BigDecimal recommendedScore;

	@Column(name = "damage_multiplier", nullable = false, precision = 6, scale = 3)
	private BigDecimal damageMultiplier;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "image_asset_id", nullable = false)
	private ImageAsset imageAsset;

	private BossPhaseTemplate(Boss boss, int phaseNo, long maxHp, BigDecimal hardMinScore,
			BigDecimal recommendedScore, BigDecimal damageMultiplier, ImageAsset imageAsset) {
		if (boss == null || imageAsset == null || phaseNo <= 0) {
			throw new DomainException(ErrorCode.VALIDATION_ERROR,
					"boss, imageAsset and positive phaseNo are required.");
		}
		this.boss = boss;
		this.phaseNo = phaseNo;
		this.maxHp = requirePositive(maxHp, "maxHp");
		this.hardMinScore = requireNonNegative(hardMinScore, "hardMinScore");
		this.recommendedScore = requireNonNegative(recommendedScore, "recommendedScore");
		if (hardMinScore != null && recommendedScore != null && recommendedScore.compareTo(hardMinScore) < 0) {
			throw new DomainException(ErrorCode.VALIDATION_ERROR,
					"recommendedScore must be greater than or equal to hardMinScore.");
		}
		this.damageMultiplier = requirePositive(damageMultiplier, "damageMultiplier");
		this.imageAsset = imageAsset;
	}

	public static BossPhaseTemplate of(Boss boss, int phaseNo, long maxHp, BigDecimal hardMinScore,
			BigDecimal recommendedScore, BigDecimal damageMultiplier, ImageAsset imageAsset) {
		return new BossPhaseTemplate(boss, phaseNo, maxHp, hardMinScore, recommendedScore, damageMultiplier,
				imageAsset);
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
