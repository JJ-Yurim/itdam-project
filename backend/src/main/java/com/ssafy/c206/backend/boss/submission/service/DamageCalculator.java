package com.ssafy.c206.backend.boss.submission.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import com.ssafy.c206.backend.boss.service.BossRunHpPolicy;
import com.ssafy.c206.backend.common.api.DomainException;
import com.ssafy.c206.backend.common.api.ErrorCode;
import org.springframework.stereotype.Component;

@Component
public class DamageCalculator {

	private final DamagePolicy policy;
	private final BossRunHpPolicy hpPolicy;

	public DamageCalculator(DamagePolicy policy, BossRunHpPolicy hpPolicy) {
		this.policy = policy;
		this.hpPolicy = hpPolicy;
	}

	public DamageCalculation calculate(DamageCalculationCommand command) {
		if (command.baseScore().compareTo(command.hardMinScore()) < 0) {
			throw new DomainException(ErrorCode.POLICY_REJECTED, "Base score does not meet this phase's hard minimum.");
		}

		Map<String, BigDecimal> strengthsByMood = sentenceStrengths(command);
		Set<String> weaknessMoodCodes = weaknessMoodCodes(command);
		BigDecimal moodBonus = cappedBonus(strengthsByMood, command, SubmissionMoodRole.THEME, weaknessMoodCodes);
		BigDecimal weaknessBonus = cappedBonus(strengthsByMood, command, SubmissionMoodRole.WEAKNESS, Set.of());
		// 최종 데미지는 MAX_DAMAGE_CAP을 초과하지 않는다(명세 §6.11). 배율이 1이면 점수
		// 구성상 자연 최대치가 상한과 같지만, 관리자가 배율을 1보다 크게 설정한 페이즈에서는
		// 이 상한이 없으면 HP 산정 근거(상한 × 목표 제출 수)가 무너진다.
		long finalDamage = Math.min(
				finalDamage(command.gameBaseAttack(), moodBonus, weaknessBonus, command.phaseModifier()),
				hpPolicy.maxDamageCap());

		return new DamageCalculation(command.baseScore(), command.wordBonus(), command.gameBaseAttack(), moodBonus, weaknessBonus,
				command.phaseModifier(), finalDamage);
	}

	private Map<String, BigDecimal> sentenceStrengths(DamageCalculationCommand command) {
		Map<String, BigDecimal> strengths = new HashMap<>();
		for (SentenceMoodSnapshot mood : command.sentenceMoods()) {
			if (strengths.putIfAbsent(mood.moodCode(), mood.strength()) != null) {
				throw new IllegalArgumentException("Sentence moods must not contain duplicate mood codes.");
			}
		}
		return strengths;
	}

	private Set<String> weaknessMoodCodes(DamageCalculationCommand command) {
		Set<String> weaknessCodes = new HashSet<>();
		for (BossMoodSnapshot mood : command.bossMoods()) {
			if (mood.role() == SubmissionMoodRole.WEAKNESS) {
				weaknessCodes.add(mood.moodCode());
			}
		}
		return weaknessCodes;
	}

	private BigDecimal cappedBonus(Map<String, BigDecimal> strengthsByMood, DamageCalculationCommand command,
			SubmissionMoodRole targetRole, Set<String> excludedMoodCodes) {
		Set<String> seenMoodCodes = new HashSet<>();
		BigDecimal total = BigDecimal.ZERO;
		for (BossMoodSnapshot mood : command.bossMoods()) {
			if (mood.role() != targetRole) {
				continue;
			}
			if (!seenMoodCodes.add(mood.moodCode())) {
				throw new IllegalArgumentException("Boss moods must not repeat a mood code for the same role.");
			}
			if (excludedMoodCodes.contains(mood.moodCode())) {
				continue;
			}
			BigDecimal strength = strengthsByMood.get(mood.moodCode());
			if (strength != null) {
				BigDecimal factor = targetRole == SubmissionMoodRole.THEME
						? policy.getThemeFactor() : policy.getWeaknessFactor();
				total = total.add(strength.multiply(mood.weight()).multiply(factor));
			}
		}
		BigDecimal cap = targetRole == SubmissionMoodRole.THEME ? policy.getThemeBonusCap() : policy.getWeaknessBonusCap();
		return total.min(cap);
	}

	private static long finalDamage(BigDecimal gameBaseAttack, BigDecimal moodBonus, BigDecimal weaknessBonus,
			BigDecimal phaseModifier) {
		try {
			return gameBaseAttack.add(moodBonus).add(weaknessBonus).multiply(phaseModifier)
					.setScale(0, RoundingMode.HALF_UP).longValueExact();
		} catch (ArithmeticException exception) {
			throw new IllegalArgumentException("Final damage must fit in a long.", exception);
		}
	}
}
