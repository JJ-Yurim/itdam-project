import { useEffect, useLayoutEffect, useRef, useState } from 'react';
import { Button } from '@/shared/components';

import { TUTORIAL_STEP_COUNT } from './steps';

interface DamiGuideProps {
  step: number;
  title: string;
  description: string;
  primaryLabel?: string;
  primaryDisabled?: boolean;
  onPrimary?: () => void;
  onBack?: () => void;
  onReset: () => void;
  onExit: () => void;
  /** 점수·HP처럼 화면 결과가 중요한 순간에는 작은 버튼으로 자동 축소한다. */
  compact?: boolean;
  /** 모바일 하단 공격 바와 함께 보일 때 다미 버튼을 그 위로 올린다. */
  lifted?: boolean;
  /** 책 상세처럼 데스크톱 안내를 화면 아래로 더 내려야 하는 단계에서 사용한다. */
  lowered?: boolean;
  /** 특정 강조 대상이 없는 안내에서는 화면 전체를 어둡게 한다. */
  backdrop?: boolean;
  /** 현재 단계에서 강조하는 요소의 id. 안내를 대상 반대편에 배치할 때 사용한다. */
  targetId?: string;
  /** 특정 단계에서 자동 계산 대신 사용할 안내 위치. */
  placementOverride?: GuidePlacement;
}

const DAMI_SPEAKING_CHARACTER_SRC = '/tutorial-assets/dami-speaking-pointing-left.png';
const DAMI_MINIMIZED_CHARACTER_SRC = '/tutorial-assets/dami-minimized-bust.png';
export type GuidePlacement = 'top' | 'bottom';

export function DamiGuide({
  step,
  title,
  description,
  primaryLabel,
  primaryDisabled = false,
  onPrimary,
  onBack,
  onReset,
  onExit,
  compact = false,
  lifted = false,
  lowered = false,
  backdrop = false,
  targetId,
  placementOverride,
}: DamiGuideProps) {
  const guideRef = useRef<HTMLElement>(null);
  const [manualState, setManualState] = useState<{
    step: number;
    expanded: boolean;
  } | null>(null);
  const [placement, setPlacement] = useState<GuidePlacement>('bottom');
  const isExpanded = manualState?.step === step ? manualState.expanded : !compact;
  const effectivePlacement = placementOverride ?? placement;

  useLayoutEffect(() => {
    const updatePlacement = () => {
      const target = targetId ? document.getElementById(targetId) : null;
      if (!target) {
        setPlacement('bottom');
        return;
      }

      const rect = target.getBoundingClientRect();
      const targetCenter = rect.top + rect.height / 2;
      setPlacement(targetCenter >= window.innerHeight / 2 ? 'top' : 'bottom');
    };

    updatePlacement();
    window.addEventListener('resize', updatePlacement);
    return () => window.removeEventListener('resize', updatePlacement);
  }, [targetId]);

  useEffect(() => {
    if (!isExpanded) return;

    const collapseOnOutsidePointer = (event: PointerEvent) => {
      if (event.target instanceof Node && guideRef.current?.contains(event.target)) return;
      setManualState({ step, expanded: false });
    };

    document.addEventListener('pointerdown', collapseOnOutsidePointer, true);
    return () => document.removeEventListener('pointerdown', collapseOnOutsidePointer, true);
  }, [isExpanded, step]);

  if (!isExpanded) {
    return (
      <button
        type="button"
        aria-label="다미 안내 펼치기"
        onClick={() => setManualState({ step, expanded: true })}
        className={[
          'tutorial-dami-guide-button fixed right-3 bottom-[calc(env(safe-area-inset-bottom)+5.5rem)] z-50 size-16 overflow-hidden rounded-full border-2 border-violet-300 bg-white shadow-xl shadow-violet-950/20 transition hover:scale-105 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-violet-600 lg:top-[6.5rem] lg:right-[max(1.5rem,calc((100vw-1180px)/2+1.5rem))] lg:bottom-auto lg:size-20',
          lifted ? 'tutorial-dami-guide--lifted' : '',
        ].join(' ')}
      >
        <img
          data-testid="dami-character"
          src={DAMI_MINIMIZED_CHARACTER_SRC}
          alt=""
          className="size-full scale-[0.95] object-contain object-center translate-y-1"
        />
      </button>
    );
  }

  return (
    <>
      {backdrop && (
        <div
          aria-hidden="true"
          data-testid="tutorial-guide-backdrop"
          className="tutorial-guide-backdrop pointer-events-none fixed inset-0 z-[46]"
        />
      )}
      <aside
        ref={guideRef}
        aria-label="다미의 튜토리얼 안내"
        className={[
          'tutorial-dami-guide pointer-events-none fixed right-3 z-50 h-[220px] w-[calc(100vw-1.5rem)] max-w-[390px]',
          'lg:right-[max(1.5rem,calc((100vw-1180px)/2+1.5rem))] lg:h-[360px] lg:w-[740px] lg:max-w-none',
          `tutorial-dami-guide--${effectivePlacement}`,
          lifted ? 'tutorial-dami-guide--lifted' : '',
          lowered ? 'tutorial-dami-guide--lowered' : '',
        ].join(' ')}
      >
        <section
          data-testid="dami-speech-bubble"
          className="tutorial-dami-speech-bubble pointer-events-auto absolute top-0 left-0 w-[calc(100%-4rem)] max-w-[310px] rounded-[1.5rem] border border-violet-200 bg-white p-3 shadow-2xl shadow-violet-950/20 lg:max-w-[520px] lg:p-6"
        >
          <span aria-hidden="true" className="tutorial-dami-speech-tail" />
          <div className="flex items-center justify-between gap-2">
            <p className="text-xs font-black text-violet-700 lg:text-base">
              다미의 안내 · {step + 1}/{TUTORIAL_STEP_COUNT}
            </p>
            <button
              type="button"
              aria-label="다미 안내 접기"
              title="안내 접기"
              onClick={() => setManualState({ step, expanded: false })}
              className="flex size-7 shrink-0 items-center justify-center rounded-full text-stone-400 hover:bg-stone-100 hover:text-stone-700 lg:size-10 lg:text-xl"
            >
              ↓
            </button>
          </div>
          <h2 className="mt-1 text-base font-black text-stone-900 min-[400px]:text-lg lg:mt-2 lg:text-3xl">
            {title}
          </h2>
          <p className="mt-1 text-xs leading-5 text-stone-600 min-[400px]:text-sm min-[400px]:leading-6 lg:mt-3 lg:text-lg lg:leading-8">
            {description}
          </p>

          <div className="mt-3 flex gap-1" aria-label="튜토리얼 진행률">
            {Array.from({ length: TUTORIAL_STEP_COUNT }, (_, index) => (
              <span
                key={index}
                aria-hidden="true"
                className={[
                  'h-1.5 flex-1 rounded-full transition-colors',
                  index <= step ? 'bg-violet-500' : 'bg-stone-200',
                ].join(' ')}
              />
            ))}
          </div>
          <div className="mt-3 flex flex-wrap items-center gap-1.5 border-t border-stone-100 pt-3 lg:mt-5 lg:gap-2 lg:pt-5">
            {onBack ? (
              <Button
                variant="ghost"
                size="sm"
                onClick={onBack}
                className="lg:min-h-12 lg:px-5 lg:text-base"
              >
                이전
              </Button>
            ) : (
              <button
                type="button"
                onClick={onReset}
                className="min-h-9 rounded-lg px-2 text-xs font-semibold text-stone-500 hover:bg-stone-50 lg:min-h-12 lg:px-4 lg:text-base"
              >
                다시 시작
              </button>
            )}
            <button
              type="button"
              onClick={onExit}
              className="ml-auto inline-flex min-h-9 items-center rounded-lg px-2 text-xs font-semibold text-stone-500 hover:bg-stone-50 lg:min-h-12 lg:px-4 lg:text-base"
            >
              튜토리얼 나가기
            </button>
            {primaryLabel && onPrimary && (
              <Button
                size="sm"
                disabled={primaryDisabled}
                onClick={onPrimary}
                className="w-full lg:min-h-12 lg:px-5 lg:text-base"
              >
                {primaryLabel}
              </Button>
            )}
          </div>
        </section>

        <div
          data-testid="dami-character-frame"
          className="tutorial-dami-character-frame pointer-events-none absolute -right-1 bottom-0 h-32 w-28 lg:right-0 lg:h-[336px] lg:w-72"
        >
          <img
            data-testid="dami-character"
            src={DAMI_SPEAKING_CHARACTER_SRC}
            alt="안내 캐릭터 다미"
            className="tutorial-dami-character size-full object-contain object-bottom"
          />
        </div>
      </aside>
    </>
  );
}
