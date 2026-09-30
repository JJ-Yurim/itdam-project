import { createPortal } from 'react-dom';

import type { ReactNode } from 'react';

type TutorialCoachMarkLabelAlign = 'start' | 'center' | 'end';

interface TutorialCoachMarkLabelProps {
  active: boolean;
  label: string;
  advanceHint?: boolean;
  labelAlign?: TutorialCoachMarkLabelAlign;
}

interface TutorialCoachMarkProps {
  active: boolean;
  label: string;
  children: ReactNode;
  className?: string;
  id?: string;
  advanceHint?: boolean;
  labelAlign?: TutorialCoachMarkLabelAlign;
  showLabel?: boolean;
  dimScreen?: boolean;
}

export function TutorialCoachMarkLabel({
  active,
  label,
  advanceHint = false,
  labelAlign = 'center',
}: TutorialCoachMarkLabelProps) {
  if (!active) return null;

  const arrowPositionClass =
    labelAlign === 'start'
      ? 'left-8'
      : labelAlign === 'end'
        ? 'right-8'
        : 'left-1/2 -translate-x-1/2';
  const markerContent = (
    <>
      <span aria-hidden="true" className="mr-1 lg:mr-1.5">
        ✦
      </span>
      {label}
      {advanceHint && (
        <span aria-hidden="true" className="ml-2 text-violet-200">
          화면을 눌러 계속
        </span>
      )}
      <span
        aria-hidden="true"
        className={`absolute bottom-0 size-2.5 translate-y-1/2 rotate-45 bg-violet-700 lg:size-[15px] ${arrowPositionClass}`}
      />
    </>
  );
  const statusLabel = advanceHint
    ? `안내: ${label}. 화면을 눌러 계속하세요`
    : `다음 행동: ${label}`;
  const labelPositionClass =
    labelAlign === 'start'
      ? 'left-0'
      : labelAlign === 'end'
        ? 'right-0'
        : 'left-1/2 -translate-x-1/2';

  return (
    <span
      role="status"
      aria-label={statusLabel}
      className={`tutorial-coachmark-label pointer-events-none absolute bottom-full z-[1] mb-2 whitespace-nowrap rounded-full bg-violet-700 px-3 py-2 text-xs font-black text-white shadow-lg shadow-violet-950/20 lg:mb-3 lg:px-[18px] lg:py-3 lg:text-lg ${labelPositionClass}`}
    >
      {markerContent}
    </span>
  );
}

/** 실제 온보딩에서 쓰는 코치마크처럼 다음 행동만 짧게 강조한다. */
export function TutorialCoachMark({
  active,
  label,
  children,
  className = '',
  id,
  advanceHint = false,
  labelAlign = 'center',
  showLabel = true,
  dimScreen = true,
}: TutorialCoachMarkProps) {
  const screenDim =
    active && dimScreen
      ? createPortal(
          <span
            aria-hidden="true"
            data-testid="tutorial-screen-dim"
            className="pointer-events-none fixed inset-0 z-[45] bg-stone-950/45"
          />,
          document.body,
        )
      : null;

  return (
    <>
      {screenDim}
      <div
        id={id}
        className={[
          'relative rounded-2xl',
          active ? 'tutorial-coachmark z-[46] bg-white' : '',
          className,
        ].join(' ')}
      >
        {active && (
          <span
            aria-hidden="true"
            data-testid="tutorial-spotlight"
            className="tutorial-spotlight pointer-events-none absolute -inset-1.5 rounded-2xl"
          />
        )}
        {showLabel && (
          <TutorialCoachMarkLabel
            active={active}
            label={label}
            advanceHint={advanceHint}
            labelAlign={labelAlign}
          />
        )}
        {children}
      </div>
    </>
  );
}
