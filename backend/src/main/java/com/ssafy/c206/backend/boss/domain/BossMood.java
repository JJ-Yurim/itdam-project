package com.ssafy.c206.backend.boss.domain;

import java.math.BigDecimal;

import com.ssafy.c206.backend.common.api.DomainException;
import com.ssafy.c206.backend.common.api.ErrorCode;
import com.ssafy.c206.backend.sentence.evaluation.domain.MoodCategory;
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
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "boss_moods", uniqueConstraints = @UniqueConstraint(
		name = "uk_boss_mood_category_role", columnNames = {"boss_id", "mood_category_id", "mood_role"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BossMood {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "boss_id", nullable = false)
	private Boss boss;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "mood_category_id", nullable = false)
	private MoodCategory moodCategory;

	@Enumerated(EnumType.STRING)
	@Column(name = "mood_role", nullable = false, length = 30)
	private MoodRole moodRole;

	@DecimalMin(value = "0.001")
	@Digits(integer = 3, fraction = 3)
	@Column(nullable = false, precision = 6, scale = 3)
	private BigDecimal weight;

	private BossMood(Boss boss, MoodCategory moodCategory, MoodRole moodRole, BigDecimal weight) {
		if (boss == null || moodCategory == null || moodRole == null) {
			throw new DomainException(ErrorCode.VALIDATION_ERROR, "boss mood references are required.");
		}
		this.boss = boss;
		this.moodCategory = moodCategory;
		this.moodRole = moodRole;
		this.weight = requirePositive(weight, "weight");
	}

	public static BossMood of(Boss boss, MoodCategory moodCategory, MoodRole moodRole, BigDecimal weight) {
		return new BossMood(boss, moodCategory, moodRole, weight);
	}

	private static BigDecimal requirePositive(BigDecimal value, String field) {
		if (value == null || value.signum() <= 0) {
			throw new DomainException(ErrorCode.VALIDATION_ERROR, field + " must be positive.");
		}
		return value;
	}
}
