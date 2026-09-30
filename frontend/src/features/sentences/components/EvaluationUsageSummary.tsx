import type { EvaluationUsage } from '../types';

interface EvaluationUsageSummaryProps {
  usage: EvaluationUsage;
}

export function EvaluationUsageSummary({ usage }: EvaluationUsageSummaryProps) {
  return (
    <div className="flex items-center gap-2 rounded-xl bg-violet-50 px-4 py-3 text-sm text-stone-700">
      <span aria-hidden="true">🔮</span>

      <span>AI 평가 권한</span>

      <strong className="ml-auto text-violet-700">
        {usage.remainingCount} / {usage.limit}회
      </strong>
    </div>
  );
}
