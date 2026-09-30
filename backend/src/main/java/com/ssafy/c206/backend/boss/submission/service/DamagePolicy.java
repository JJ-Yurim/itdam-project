package com.ssafy.c206.backend.boss.submission.service;

import java.math.BigDecimal;
import java.util.Objects;

import lombok.Getter;

/** Centralized, immutable configuration for the MVP boss-damage formula. */
@Getter
public final class DamagePolicy {

	private final BigDecimal themeFactor;
	private final BigDecimal themeBonusCap;
	private final BigDecimal weaknessFactor;
	private final BigDecimal weaknessBonusCap;

	public DamagePolicy(BigDecimal themeFactor, BigDecimal themeBonusCap, BigDecimal weaknessFactor,
			BigDecimal weaknessBonusCap) {
		this.themeFactor = requireNonNegative(themeFactor, "themeFactor");
		this.themeBonusCap = requireNonNegative(themeBonusCap, "themeBonusCap");
		this.weaknessFactor = requireNonNegative(weaknessFactor, "weaknessFactor");
		this.weaknessBonusCap = requireNonNegative(weaknessBonusCap, "weaknessBonusCap");
	}

	public static DamagePolicy defaults() {
		return new DamagePolicy(BigDecimal.TEN, BigDecimal.valueOf(15), BigDecimal.valueOf(20),
				BigDecimal.valueOf(25));
	}

	private static BigDecimal requireNonNegative(BigDecimal value, String field) {
		Objects.requireNonNull(value, field + " must not be null.");
		if (value.signum() < 0) {
			throw new IllegalArgumentException(field + " must not be negative.");
		}
		return value;
	}
}
