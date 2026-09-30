package com.ssafy.c206.backend.boss.submission.service;

import java.math.BigDecimal;

/**
 * Persistable calculation snapshot; recommendedScore is informational only.
 *
 * <p>{@code finalDamage}는 명세 §6.11의 MAX_DAMAGE_CAP 상한이 적용된 값이므로,
 * 상한이 개입한 제출에서는 구성값의 곱보다 작을 수 있다. 데미지 내역을 화면에 보여주는
 * 소비자는 {@code (gameBaseAttack + moodBonus + weaknessBonus) × phaseModifier}가
 * {@code finalDamage}와 항상 같다고 가정해서는 안 된다.
 */
public record DamageCalculation(
		BigDecimal baseScore,
		BigDecimal wordBonus,
		BigDecimal gameBaseAttack,
		BigDecimal moodBonus,
		BigDecimal weaknessBonus,
		BigDecimal phaseModifier,
		long finalDamage
) {
}
