import { TUTORIAL_STEP_COUNT, TUTORIAL_STEP_LABELS } from './steps';

interface TutorialProgressProps {
  currentStep: number;
}

export function TutorialProgress({ currentStep }: TutorialProgressProps) {
  const progress = (currentStep / (TUTORIAL_STEP_LABELS.length - 1)) * 100;

  return (
    <nav
      aria-label="튜토리얼 진행 단계"
      className="rounded-2xl border border-violet-100 bg-white p-4 shadow-sm"
    >
      <div className="relative">
        <div className="absolute top-3.5 right-4 left-4 h-1 rounded-full bg-stone-100" />
        <div
          className="absolute top-3.5 left-4 h-1 rounded-full bg-violet-500 transition-[width] duration-500 motion-reduce:transition-none"
          style={{ width: `calc((100% - 2rem) * ${progress / 100})` }}
        />
        <ol
          className="relative grid"
          style={{ gridTemplateColumns: `repeat(${TUTORIAL_STEP_COUNT}, minmax(0, 1fr))` }}
        >
          {TUTORIAL_STEP_LABELS.map((label, index) => {
            const isCurrent = index === currentStep;
            const isComplete = index < currentStep;

            return (
              <li
                key={label}
                aria-current={isCurrent ? 'step' : undefined}
                className="flex min-w-0 flex-col items-center gap-2"
              >
                <span
                  className={[
                    'flex size-8 items-center justify-center rounded-full border-2 text-xs font-extrabold transition-colors',
                    isCurrent
                      ? 'border-violet-600 bg-violet-600 text-white'
                      : isComplete
                        ? 'border-violet-500 bg-violet-100 text-violet-800'
                        : 'border-stone-200 bg-white text-stone-400',
                  ].join(' ')}
                >
                  {isComplete ? '✓' : index + 1}
                </span>
                <span
                  className={[
                    'max-w-full truncate text-[10px] font-semibold min-[400px]:text-xs',
                    isCurrent ? 'text-violet-800' : 'text-stone-500',
                  ].join(' ')}
                >
                  {label}
                </span>
              </li>
            );
          })}
        </ol>
      </div>
    </nav>
  );
}
