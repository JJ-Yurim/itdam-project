import { Spinner } from '@/shared/components';

const evaluationSteps = ['문장 흐름', '표현력', '분위기'] as const;

export function SentenceEvaluationLoading() {
  return (
    <section
      role="status"
      aria-live="polite"
      aria-labelledby="sentence-evaluation-loading-title"
      className="relative overflow-hidden rounded-2xl border border-violet-200 bg-gradient-to-br from-violet-50 via-white to-indigo-50 p-5 shadow-sm"
    >
      <div className="flex items-center gap-4">
        <span className="relative flex size-14 shrink-0 items-center justify-center rounded-full bg-violet-100 text-violet-600">
          <Spinner className="size-8" />
          <span className="absolute top-0 right-0 size-2.5 animate-ping rounded-full bg-amber-300 motion-reduce:animate-none" />
        </span>

        <div className="min-w-0">
          <h2 id="sentence-evaluation-loading-title" className="font-extrabold text-violet-950">
            AI가 문장의 힘을 읽고 있어요
          </h2>
          <p className="mt-1 text-sm leading-6 text-violet-800">
            문장의 흐름과 표현, 분위기를 차례로 살피고 있어요.
          </p>
        </div>
      </div>

      <div className="mt-5 flex flex-wrap gap-2" aria-hidden="true">
        {evaluationSteps.map((step, index) => (
          <span
            key={step}
            className="animate-pulse rounded-full border border-violet-200 bg-white px-3 py-1.5 text-xs font-semibold text-violet-700 motion-reduce:animate-none"
            style={{ animationDelay: `${index * 220}ms` }}
          >
            {step} 분석 중
          </span>
        ))}
      </div>

      <div
        role="progressbar"
        aria-label="AI 문장 평가 진행 중"
        aria-valuetext="문장을 분석하고 있어요"
        className="mt-4 h-2 overflow-hidden rounded-full bg-violet-100"
      >
        <span className="sentence-evaluation-progress block h-full w-2/5 rounded-full bg-gradient-to-r from-violet-500 via-fuchsia-400 to-amber-300" />
      </div>

      <p className="mt-3 text-xs font-medium text-stone-500">
        결과가 준비되면 이 화면에 자동으로 표시돼요.
      </p>
    </section>
  );
}
