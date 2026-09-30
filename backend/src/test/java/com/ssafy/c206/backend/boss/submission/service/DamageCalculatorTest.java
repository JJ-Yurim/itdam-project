package com.ssafy.c206.backend.boss.submission.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.List;

import com.ssafy.c206.backend.boss.service.BossRunHpPolicy;
import com.ssafy.c206.backend.common.api.DomainException;
import com.ssafy.c206.backend.common.api.ErrorCode;
import org.junit.jupiter.api.Test;

class DamageCalculatorTest {

	private static final long MAX_DAMAGE_CAP = 170L;

	private final DamageCalculator calculator = new DamageCalculator(DamagePolicy.defaults(),
			new BossRunHpPolicy(MAX_DAMAGE_CAP, 10));

	@Test
	void rejectsBaseScoreBelowHardMinimum() {
		assertThatThrownBy(() -> calculator.calculate(command("79.99", "80.00", "100", "1", List.of(), List.of())))
				.isInstanceOf(DomainException.class)
				.extracting(exception -> ((DomainException) exception).getErrorCode())
				.isEqualTo(ErrorCode.POLICY_REJECTED);
	}

	@Test
	void recommendedScoreIsInformationalAndDoesNotChangeDamage() {
		DamageCalculation result = calculator.calculate(command("80", "40", "70", "999", List.of(), List.of()));

		assertThat(result.finalDamage()).isEqualTo(40L);
		assertThat(result.moodBonus()).isEqualByComparingTo("0");
		assertThat(result.weaknessBonus()).isEqualByComparingTo("0");
	}

	@Test
	void appliesThemeAndWeaknessCapsAndWeaknessWinsOverDuplicateMood() {
		DamageCalculation result = calculator.calculate(command("80", "100", "70", "90", "1",
				List.of(
						new SentenceMoodSnapshot("JOY", new BigDecimal("1.000")),
						new SentenceMoodSnapshot("HOPE", new BigDecimal("1.000")),
						new SentenceMoodSnapshot("LOVE", new BigDecimal("0.500")),
						new SentenceMoodSnapshot("CALM", new BigDecimal("1.000"))),
				List.of(
						new BossMoodSnapshot("JOY", SubmissionMoodRole.THEME, BigDecimal.ONE),
						new BossMoodSnapshot("JOY", SubmissionMoodRole.WEAKNESS, BigDecimal.ONE),
						new BossMoodSnapshot("HOPE", SubmissionMoodRole.THEME, BigDecimal.ONE),
						new BossMoodSnapshot("LOVE", SubmissionMoodRole.THEME, BigDecimal.valueOf(2)),
						new BossMoodSnapshot("LOVE", SubmissionMoodRole.WEAKNESS, BigDecimal.valueOf(3)),
						new BossMoodSnapshot("CALM", SubmissionMoodRole.THEME, BigDecimal.valueOf(2)))));

		assertThat(result.moodBonus()).isEqualByComparingTo("15");
		assertThat(result.weaknessBonus()).isEqualByComparingTo("25");
		assertThat(result.finalDamage()).isEqualTo(140L);
	}

	@Test
	void roundsFinalDamageHalfUp() {
		DamageCalculation rounded = calculator.calculate(command("80", "100.20", "0", "0", "1.005", List.of(), List.of()));

		assertThat(rounded.finalDamage()).isEqualTo(101L);
	}

	@Test
	void capsFinalDamageAtTheHpPolicyMaximum() {
		// 배율 1에서는 점수 구성상 자연 최대치가 상한 이하라 상한이 개입하지 않는다.
		DamageCalculation underCap = calculator.calculate(command("80", "169", "0", "0", "1", List.of(), List.of()));
		DamageCalculation atCap = calculator.calculate(command("80", "170", "0", "0", "1", List.of(), List.of()));
		// 배율이 1보다 크면 상한 없이는 HP 산정 근거(상한 × 목표 제출 수)가 무너진다(명세 §6.11).
		DamageCalculation overCap = calculator.calculate(command("80", "170", "0", "0", "2", List.of(), List.of()));

		assertThat(underCap.finalDamage()).isEqualTo(169L);
		assertThat(atCap.finalDamage()).isEqualTo(MAX_DAMAGE_CAP);
		assertThat(overCap.finalDamage()).isEqualTo(MAX_DAMAGE_CAP);
	}

	@Test
	void validatesSnapshotValuesAndRejectsDuplicateInputMoods() {
		assertThatIllegalArgumentException().isThrownBy(() -> new SentenceMoodSnapshot("JOY", new BigDecimal("1.001")));
		assertThatIllegalArgumentException().isThrownBy(() -> new BossMoodSnapshot("JOY", SubmissionMoodRole.THEME,
				new BigDecimal("-0.001")));
		assertThatIllegalArgumentException().isThrownBy(() -> calculator.calculate(command("80", "90", "0", "0", "1",
				List.of(new SentenceMoodSnapshot("JOY", BigDecimal.ONE), new SentenceMoodSnapshot("JOY", BigDecimal.ONE)),
				List.of())));
	}

	private static DamageCalculationCommand command(String baseScore, String gameBaseAttack, String hardMinScore,
			String recommendedScore, String phaseModifier, List<SentenceMoodSnapshot> sentenceMoods,
			List<BossMoodSnapshot> bossMoods) {
		return new DamageCalculationCommand(new BigDecimal(baseScore), BigDecimal.ZERO, new BigDecimal(gameBaseAttack),
				new BigDecimal(hardMinScore), new BigDecimal(recommendedScore), new BigDecimal(phaseModifier),
				sentenceMoods, bossMoods);
	}

	private static DamageCalculationCommand command(String baseScore, String gameBaseAttack, String hardMinScore,
			String recommendedScore, List<SentenceMoodSnapshot> sentenceMoods, List<BossMoodSnapshot> bossMoods) {
		return command(baseScore, gameBaseAttack, hardMinScore, recommendedScore, "1", sentenceMoods, bossMoods);
	}
}
