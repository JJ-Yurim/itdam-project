package com.ssafy.c206.backend.boss.service;

import com.ssafy.c206.backend.common.api.DomainException;
import com.ssafy.c206.backend.common.api.ErrorCode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 페이즈 HP 산정 원칙 (명세 §6.11): Phase HP = MAX_DAMAGE_CAP × TARGET_MIN_VALID_SUBMISSIONS_PER_PHASE.
 * MAX_DAMAGE_CAP 기본값 170 = 기본 점수 100 + 단어 보너스 30 + 테마 보너스 캡 15 + 약점 보너스 캡 25
 * (배율 1 기준, EvaluationPolicy·DamagePolicy 현재 튜닝값에서 도출). 값은 튜닝 대상이므로 프로퍼티로 조정한다.
 */
@Component
public class BossRunHpPolicy {

	private final long maxDamageCap;
	private final int targetMinValidSubmissionsPerPhase;

	public BossRunHpPolicy(
			@Value("${boss.run.hp.max-damage-cap:170}") long maxDamageCap,
			@Value("${boss.run.hp.target-min-valid-submissions-per-phase:10}") int targetMinValidSubmissionsPerPhase) {
		if (maxDamageCap <= 0 || targetMinValidSubmissionsPerPhase <= 0) {
			throw new DomainException(ErrorCode.INTERNAL_ERROR, "Boss run HP policy values must be positive.");
		}
		this.maxDamageCap = maxDamageCap;
		this.targetMinValidSubmissionsPerPhase = targetMinValidSubmissionsPerPhase;
	}

	public long defaultPhaseHp() {
		return maxDamageCap * targetMinValidSubmissionsPerPhase;
	}

	/**
	 * 제출 1회의 최종 데미지 상한 (명세 §6.11).
	 *
	 * <p>페이즈 HP가 이 값 × 목표 제출 수로 산정되므로, 데미지 계산이 같은 값을 상한으로
	 * 쓰지 않으면 "최고 데미지 문장만 제출되어도 최소 목표 제출 수가 필요하다"는 HP 설계
	 * 근거가 무너진다. 두 용도가 항상 같은 값을 보도록 이 빈을 단일 출처로 공유한다.
	 */
	public long maxDamageCap() {
		return maxDamageCap;
	}
}
