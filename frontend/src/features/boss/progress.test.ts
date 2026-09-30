import { describe, expect, it } from 'vitest';

import { calculateBossProgressPercent, calculateBossRemainingHpPercent } from './progress';

describe('calculateBossProgressPercent', () => {
  it.each([
    { maxHp: 1_000, remainingHp: 1_000, expected: 0 },
    { maxHp: 1_000, remainingHp: 890, expected: 11 },
    { maxHp: 1_000, remainingHp: 565, expected: 43.5 },
    { maxHp: 1_000, remainingHp: 0, expected: 100 },
  ])('누적 피해 비율을 공략 진행률로 계산한다', ({ maxHp, remainingHp, expected }) => {
    expect(calculateBossProgressPercent(maxHp, remainingHp)).toBe(expected);
  });

  it('잘못된 HP 값도 0~100% 범위로 제한한다', () => {
    expect(calculateBossProgressPercent(0, 0)).toBe(0);
    expect(calculateBossProgressPercent(1_000, 1_100)).toBe(0);
    expect(calculateBossProgressPercent(1_000, -100)).toBe(100);
  });
});

describe('calculateBossRemainingHpPercent', () => {
  it.each([
    { maxHp: 1_000, remainingHp: 1_000, expected: 100 },
    { maxHp: 1_000, remainingHp: 930, expected: 93 },
    { maxHp: 1_000, remainingHp: 565, expected: 56.5 },
    { maxHp: 1_000, remainingHp: 0, expected: 0 },
  ])('남은 HP 비율을 계산한다', ({ maxHp, remainingHp, expected }) => {
    expect(calculateBossRemainingHpPercent(maxHp, remainingHp)).toBe(expected);
  });
});
