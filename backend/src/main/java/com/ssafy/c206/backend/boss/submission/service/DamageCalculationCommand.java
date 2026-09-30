package com.ssafy.c206.backend.boss.submission.service;

import java.math.BigDecimal;
import java.util.List;

/** All values needed to create the immutable BossSubmission damage snapshot. */
public record DamageCalculationCommand(
		BigDecimal baseScore,
		BigDecimal wordBonus,
		BigDecimal gameBaseAttack,
		BigDecimal hardMinScore,
		BigDecimal recommendedScore,
		BigDecimal phaseModifier,
		List<SentenceMoodSnapshot> sentenceMoods,
		List<BossMoodSnapshot> bossMoods
) {

	public DamageCalculationCommand {
		baseScore = requireNonNegative(baseScore, "baseScore");
		wordBonus = requireNonNegative(wordBonus, "wordBonus");
		gameBaseAttack = requireNonNegative(gameBaseAttack, "gameBaseAttack");
		hardMinScore = hardMinScore == null ? BigDecimal.ZERO : requireNonNegative(hardMinScore, "hardMinScore");
		if (recommendedScore != null) {
			recommendedScore = requireNonNegative(recommendedScore, "recommendedScore");
		}
		if (phaseModifier == null || phaseModifier.signum() <= 0) {
			throw new IllegalArgumentException("phaseModifier must be positive.");
		}
		sentenceMoods = List.copyOf(requireList(sentenceMoods, "sentenceMoods"));
		bossMoods = List.copyOf(requireList(bossMoods, "bossMoods"));
	}

	/**
	 * 문장·평가 기준은 그대로 두고 페이즈 값만 바꾼 커맨드를 만든다.
	 *
	 * <p>데미지를 락 밖에서 계산한 뒤 반영 시점에 페이즈가 넘어가 있으면, 새 페이즈의 하한과
	 * 배율로 다시 계산해야 한다. 그때 문장·평가 값을 다시 읽지 않기 위한 복사다.
	 */
	public DamageCalculationCommand withPhase(BigDecimal hardMinScore, BigDecimal recommendedScore,
			BigDecimal phaseModifier) {
		return new DamageCalculationCommand(baseScore, wordBonus, gameBaseAttack, hardMinScore, recommendedScore,
				phaseModifier, sentenceMoods, bossMoods);
	}

	private static BigDecimal requireNonNegative(BigDecimal value, String field) {
		if (value == null || value.signum() < 0) {
			throw new IllegalArgumentException(field + " must not be negative.");
		}
		return value;
	}

	private static <T> List<T> requireList(List<T> values, String field) {
		if (values == null || values.stream().anyMatch(value -> value == null)) {
			throw new IllegalArgumentException(field + " must not contain null.");
		}
		return values;
	}
}
