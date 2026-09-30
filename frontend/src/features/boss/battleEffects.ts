import { useEffect, useRef, useState } from 'react';

import type { BossRunHit } from './useBossRunStream';

export type BossAttackIntensity = 'normal' | 'strong';

/**
 * 현재 페이즈의 점수 기준으로 공격 연출 강도를 정한다.
 *
 * 정상 제출은 최소 기준을 이미 통과했으므로 최소~권장 미만은 일반 공격, 권장 이상은
 * 강한 공격이다. 오래된 데이터처럼 기준이 뒤집혀 있으면 최소 기준을 권장 기준의 하한으로
 * 사용하고, 숫자가 유효하지 않으면 과한 연출을 피하기 위해 일반 공격으로 처리한다.
 */
export function getBossAttackIntensity(
  baseScore: number,
  hardMinScore: number,
  recommendedScore: number,
): BossAttackIntensity {
  if (![baseScore, hardMinScore, recommendedScore].every(Number.isFinite)) {
    return 'normal';
  }

  const minimum = Math.max(0, hardMinScore);
  const strongThreshold = Math.max(minimum, recommendedScore);

  return baseScore >= strongThreshold ? 'strong' : 'normal';
}

/** 방금 깎인 HP 구간. from은 타격 전 비율, to는 타격 후 비율이다. */
export interface BossHpLoss {
  from: number;
  to: number;
  key: number;
}

/**
 * HP 비율이 줄어든 순간을 잡아 방금 잃은 구간을 돌려준다.
 *
 * SSE의 타격 이벤트가 아니라 HP 값 자체의 변화를 보기 때문에, 실시간이 끊겨
 * 주기 갱신으로 강등된 사용자에게도 잔상 연출이 그대로 동작한다.
 *
 * percent가 null이면 비교하지 않는다. 현재 페이즈가 없는 회차(시작 전·종료)를 0%로
 * 간주하면 전투를 보던 중 회차가 끝날 때 "45% → 0%"라는 유령 손실이 바 전체에 남는다.
 */
export function useBossHpLoss(percent: number | null): BossHpLoss | null {
  const [loss, setLoss] = useState<BossHpLoss | null>(null);
  const previousPercentRef = useRef(percent);
  const lossCountRef = useRef(0);

  useEffect(() => {
    if (percent === null) {
      return;
    }

    const previous = previousPercentRef.current;
    previousPercentRef.current = percent;

    if (previous === null || percent >= previous) {
      return;
    }

    lossCountRef.current += 1;
    setLoss({ from: previous, to: percent, key: lossCountRef.current });
  }, [percent]);

  return loss;
}

/**
 * 서로 다른 출처에서 오는 타격을 하나로 합친다.
 *
 * 출처별로 마지막에 본 key를 기억해 **새로 도착한 타격만** 재생한다. 실시간이 끊겨
 * 출처가 바뀌는 순간에도 이미 재생한 타격이 되살아나지 않는다. 재생 대상의 key는
 * 출처와 무관한 단조 증가 값으로 다시 매겨, 두 출처의 key 공간이 섞이지 않게 한다.
 */
export function useLatestBossHit(
  streamHit: BossRunHit | null,
  ownHit: BossRunHit | null,
): BossRunHit | null {
  const [hit, setHit] = useState<BossRunHit | null>(null);
  const seenRef = useRef<{ stream: number | null; own: number | null }>({
    stream: null,
    own: null,
  });
  const sequenceRef = useRef(0);

  useEffect(() => {
    const seen = seenRef.current;
    const arrived =
      ownHit !== null && ownHit.key !== seen.own
        ? ownHit
        : streamHit !== null && streamHit.key !== seen.stream
          ? streamHit
          : null;

    seenRef.current = {
      stream: streamHit?.key ?? seen.stream,
      own: ownHit?.key ?? seen.own,
    };

    if (arrived === null) {
      return;
    }

    sequenceRef.current += 1;
    setHit({ damage: arrived.damage, baseScore: arrived.baseScore, key: sequenceRef.current });
  }, [ownHit, streamHit]);

  return hit;
}

/** 돌파한 페이즈와 새로 시작한 페이즈. */
export interface BossPhaseAdvance {
  from: number;
  to: number;
  key: number;
}

/**
 * 페이즈 번호가 올라간 순간을 한 번만 알린다.
 *
 * 서버 상태(현재 페이즈 번호)의 변화를 기준으로 하므로 실시간·주기 갱신 어느
 * 경로로 갱신되든 같은 시점에 감지된다. 첫 렌더는 비교 대상이 없어 알리지 않는다.
 */
export function useBossPhaseAdvance(phaseNo: number | undefined): BossPhaseAdvance | null {
  const [advance, setAdvance] = useState<BossPhaseAdvance | null>(null);
  const previousPhaseNoRef = useRef<number | undefined>(undefined);

  useEffect(() => {
    if (phaseNo === undefined) {
      return;
    }

    const previous = previousPhaseNoRef.current;
    previousPhaseNoRef.current = phaseNo;

    if (previous === undefined || phaseNo <= previous) {
      return;
    }

    setAdvance({ from: previous, to: phaseNo, key: phaseNo });
  }, [phaseNo]);

  return advance;
}

/** 보스가 일반 타격을 맞고 흔들리는 궤적. 이동은 4px 이내로 제한한다. */
const RECOIL_KEYFRAMES: Keyframe[] = [
  { transform: 'translate3d(0, 0, 0)' },
  { transform: 'translate3d(-4px, 1px, 0)', offset: 0.25 },
  { transform: 'translate3d(3px, -1px, 0)', offset: 0.55 },
  { transform: 'translate3d(-2px, 0, 0)', offset: 0.8 },
  { transform: 'translate3d(0, 0, 0)' },
];

/** 강한 공격도 카드 밖으로 튀지 않도록 7px 이내에서만 반동한다. */
const STRONG_RECOIL_KEYFRAMES: Keyframe[] = [
  { transform: 'translate3d(0, 0, 0) scale(1)' },
  { transform: 'translate3d(-7px, 2px, 0) scale(1.03)', offset: 0.2 },
  { transform: 'translate3d(6px, -2px, 0) scale(0.98)', offset: 0.48 },
  { transform: 'translate3d(-4px, 1px, 0) scale(1.01)', offset: 0.72 },
  { transform: 'translate3d(0, 0, 0) scale(1)' },
];

const RECOIL_DURATION_MS = 280;
/** CSS 공격 시퀀스와 Web Animations API 반동이 공유하는 투사체 이동 시간. */
export const BOSS_PROJECTILE_TRAVEL_MS = 300;

/**
 * 타격마다 보스 반동을 다시 재생한다.
 *
 * CSS 클래스를 붙였다 떼는 방식은 같은 요소에서 애니메이션이 다시 시작되지 않아
 * 상태를 한 프레임 비워야 하는데, 그 방식은 렌더를 두 번 유발한다. 반동은 명령형
 * 재생이 본질이라 Web Animations API로 직접 다룬다. 이미지를 remount하지 않으므로
 * 타격이 이어져도 보스 이미지가 다시 로드되지 않는다.
 */
export function useBossRecoil(hitKey: number | null, intensity: BossAttackIntensity = 'normal') {
  const targetRef = useRef<HTMLElement | null>(null);
  const intensityRef = useRef(intensity);

  // 강도 변경 자체는 반동을 다시 재생하지 않되, 다음 hitKey가 도착하면 최신 강도를 쓴다.
  // 이 효과를 재생 효과보다 먼저 선언해 두 값이 동시에 바뀐 렌더에서도 ref가 먼저 갱신된다.
  useEffect(() => {
    intensityRef.current = intensity;
  }, [intensity]);

  useEffect(() => {
    const element = targetRef.current;

    // jsdom처럼 애니메이션 API가 없는 환경과 모션 최소화 설정에서는 재생하지 않는다.
    if (hitKey === null || typeof element?.animate !== 'function') {
      return;
    }

    if (window.matchMedia?.('(prefers-reduced-motion: reduce)').matches) {
      return;
    }

    const currentIntensity = intensityRef.current;
    const animation = element.animate(
      currentIntensity === 'strong' ? STRONG_RECOIL_KEYFRAMES : RECOIL_KEYFRAMES,
      {
        delay: BOSS_PROJECTILE_TRAVEL_MS,
        duration: currentIntensity === 'strong' ? RECOIL_DURATION_MS + 100 : RECOIL_DURATION_MS,
        easing: currentIntensity === 'strong' ? 'cubic-bezier(0.2, 0.9, 0.25, 1)' : 'ease-out',
      },
    );

    return () => animation.cancel();
  }, [hitKey]);

  return targetRef;
}
