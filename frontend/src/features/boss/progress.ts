/**
 * 서버의 progressPercent 계약과 동일한 보스 공략 진행률을 계산한다.
 *
 * 공략 진행률은 남은 HP 비율이 아니라 누적 피해 비율이며,
 * 서버 응답의 정밀도에 맞춰 소수 둘째 자리까지 유지한다.
 */
export function calculateBossProgressPercent(maxHp: number, remainingHp: number): number {
  const remainingHpPercent = calculateBossRemainingHpPercent(maxHp, remainingHp);

  return maxHp > 0 ? Math.round((100 - remainingHpPercent) * 100) / 100 : 0;
}

/** 목록과 상세 화면에서 공통으로 사용하는 남은 HP 비율을 계산한다. */
export function calculateBossRemainingHpPercent(maxHp: number, remainingHp: number): number {
  if (maxHp <= 0) {
    return 0;
  }

  const clampedRemainingHp = Math.max(0, Math.min(maxHp, remainingHp));
  const remainingHpPercent = (clampedRemainingHp / maxHp) * 100;

  return Math.round(remainingHpPercent * 100) / 100;
}
