import { useEffect, useState } from 'react';
import type { CSSProperties, ReactNode } from 'react';

import { BOSS_PROJECTILE_TRAVEL_MS, useBossRecoil } from '../battleEffects';
import type { BossAttackIntensity, BossHpLoss, BossPhaseAdvance } from '../battleEffects';
import type { BossRunHit } from '../useBossRunStream';

/**
 * 보스 이미지를 감싸 타격 연출(반동·충격 링·데미지 수치)을 얹는다.
 *
 * 반동은 이미지에만 걸고 수치와 링은 그 바깥에 두어, 흔들림이 수치까지 흔들지 않는다.
 */
interface BossHitStageProps {
  hit: BossRunHit | null;
  children: ReactNode;
  intensity?: BossAttackIntensity;
  charging?: boolean;
}

export function BossHitStage({
  hit,
  children,
  intensity = 'normal',
  charging = false,
}: BossHitStageProps) {
  const recoilRef = useBossRecoil(hit?.key ?? null, intensity);

  return (
    <span
      className="boss-hit-stage relative inline-flex"
      data-attack-intensity={hit ? intensity : undefined}
      style={
        {
          '--boss-projectile-travel': `${BOSS_PROJECTILE_TRAVEL_MS}ms`,
        } as CSSProperties
      }
    >
      {charging && (
        <span
          role="status"
          aria-label="공격 마법 충전 중"
          className="boss-charge pointer-events-none absolute inset-[-10%] rounded-full"
        />
      )}

      <span ref={recoilRef} className="relative z-[1] inline-flex">
        {children}
      </span>

      {hit && (
        <span
          key={hit.key}
          aria-hidden="true"
          className="pointer-events-none absolute inset-0 z-10"
        >
          <span
            className={[
              'boss-projectile absolute size-8 rounded-full',
              intensity === 'strong' ? 'boss-projectile-strong' : '',
            ].join(' ')}
          >
            <svg viewBox="0 0 32 32" className="size-full drop-shadow-md">
              <path
                d="M16 1.8 19.8 12 30 16l-10.2 4L16 30.2 12.2 20 2 16l10.2-4L16 1.8Z"
                fill="currentColor"
              />
              <circle cx="16" cy="16" r="4.2" fill="white" fillOpacity="0.92" />
            </svg>
          </span>
          <span
            className={[
              'boss-impact absolute inset-0 rounded-full opacity-0 ring-4',
              intensity === 'strong' ? 'ring-amber-300' : 'ring-violet-400',
            ].join(' ')}
          />
          <span
            className={[
              'boss-hit-flash absolute inset-[-8%] rounded-full opacity-0',
              intensity === 'strong' ? 'bg-amber-200/70' : 'bg-violet-200/60',
            ].join(' ')}
          />
          <BossParticleBurst intensity={intensity} />
        </span>
      )}

      <DamageFloat hit={hit} intensity={intensity} />
    </span>
  );
}

/** index.css에 개별 방향이 정의된 파티클 수. 초과 항목은 CSS 기본 방향(중앙)을 사용한다. */
const PARTICLE_COUNT = 8;

function BossParticleBurst({ intensity }: { intensity: BossAttackIntensity }) {
  return (
    <span
      className={[
        'boss-particles absolute inset-0',
        intensity === 'strong' ? 'boss-particles-strong' : '',
      ].join(' ')}
    >
      {Array.from({ length: PARTICLE_COUNT }, (_, index) => (
        <span
          key={index}
          data-particle={index + 1}
          className="boss-particle absolute top-1/2 left-1/2 size-2 rounded-full bg-amber-300 opacity-0 shadow-[0_0_8px_rgba(251,191,36,0.9)]"
        />
      ))}
    </span>
  );
}

/**
 * 타격 데미지를 보스 위에 띄운다. 같은 값의 연속 타격도 hit.key가 달라
 * 연출이 다시 실행된다.
 */
function DamageFloat({
  hit,
  intensity,
}: {
  hit: BossRunHit | null;
  intensity: BossAttackIntensity;
}) {
  // 표시 여부는 "아직 치우지 않은 최신 타격"으로 파생한다. 타이머는 치움 표시만 남긴다.
  const [dismissedKey, setDismissedKey] = useState<number | null>(null);

  useEffect(() => {
    if (hit === null) {
      return;
    }

    const timeoutId = window.setTimeout(() => setDismissedKey(hit.key), 1_200);

    return () => window.clearTimeout(timeoutId);
  }, [hit]);

  if (hit === null || hit.key === dismissedKey) {
    return null;
  }

  return (
    <span
      key={hit.key}
      role="status"
      aria-label={`${hit.damage.toLocaleString()} 데미지`}
      className={[
        'boss-damage-float pointer-events-none absolute -top-5 left-1/2 z-20 flex -translate-x-1/2 flex-col items-center whitespace-nowrap font-extrabold',
        intensity === 'strong' ? 'text-amber-500' : 'text-rose-500',
      ].join(' ')}
    >
      {intensity === 'strong' && (
        <span aria-hidden="true" className="text-[10px] tracking-[0.14em] text-amber-600">
          강한 공격!
        </span>
      )}
      <span className={intensity === 'strong' ? 'text-3xl' : 'text-xl'}>
        -{hit.damage.toLocaleString()}
      </span>
    </span>
  );
}

/**
 * HP 바 안에서 방금 잃은 구간을 잔상으로 보여준다.
 * HP 바 컨테이너가 position: relative여야 한다.
 */
export function BossHpLossTrail({ loss }: { loss: BossHpLoss | null }) {
  if (loss === null) {
    return null;
  }

  // 아주 작은 피해도 한 줄기는 보이도록 최소 폭을 준다.
  const width = Math.max(loss.from - loss.to, 0.6);

  return (
    <span
      key={loss.key}
      aria-hidden="true"
      className="boss-hp-loss pointer-events-none absolute inset-y-0 bg-rose-400 opacity-0"
      style={{ left: `${loss.to}%`, width: `${width}%` }}
    />
  );
}

/**
 * 페이즈를 넘긴 순간을 보스 위에 알린다. 조작을 가리지 않도록 클릭을 통과시키고
 * 스스로 사라진다.
 */
export function BossPhaseClearBanner({ advance }: { advance: BossPhaseAdvance | null }) {
  const [dismissedKey, setDismissedKey] = useState<number | null>(null);

  useEffect(() => {
    if (advance === null) {
      return;
    }

    const timeoutId = window.setTimeout(() => setDismissedKey(advance.key), 2_200);

    return () => window.clearTimeout(timeoutId);
  }, [advance]);

  if (advance === null || advance.key === dismissedKey) {
    return null;
  }

  return (
    // 바깥 요소가 중앙 정렬을, 안쪽 요소가 애니메이션을 담당한다. 한 요소에서 둘을 함께
    // 하면 정렬 transform과 keyframe의 transform이 서로를 덮어쓴다.
    <div className="pointer-events-none absolute top-1/2 left-1/2 z-10 -translate-x-1/2 -translate-y-1/2">
      <p
        key={advance.key}
        role="status"
        className="boss-phase-clear flex flex-col rounded-card bg-stone-900/85 px-5 py-3 text-center whitespace-nowrap text-white shadow-lg"
      >
        <strong className="text-lg font-extrabold">Phase {advance.from} 돌파!</strong>
        <span className="mt-0.5 text-xs text-white/80">Phase {advance.to}가 시작됐어요</span>
      </p>
    </div>
  );
}
