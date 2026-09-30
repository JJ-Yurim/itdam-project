import type { PropsWithChildren } from 'react';
import type { SentenceEvaluationResponse } from '../types';
import { getMoodLabel } from '@/shared/moods';
import { SentenceEvaluationLoading } from './SentenceEvaluationLoading';

export interface SentenceEvaluationResultProps {
  evaluation: SentenceEvaluationResponse;
}

export function SentenceEvaluationResultCard({ children }: PropsWithChildren) {
  return (
    <section
      aria-labelledby="evaluation-result-title"
      className="rounded-2xl border border-violet-200 bg-white p-5 shadow-sm"
    >
      <h2 id="evaluation-result-title" className="text-lg font-extrabold text-stone-900">
        ✨ AI 문장 평가 결과
      </h2>
      {children}
    </section>
  );
}

export function SentenceEvaluationScoreSummary({ evaluation }: SentenceEvaluationResultProps) {
  if (!evaluation.scores) return null;

  return (
    <dl aria-label="핵심 평가 점수" className="mt-4 grid grid-cols-3 gap-2">
      <div className="rounded-xl bg-violet-50 p-3 text-center">
        <dt className="text-xs text-stone-500">기본 점수</dt>
        <dd className="mt-1 text-xl font-extrabold text-violet-800">
          {evaluation.scores.baseScore}
        </dd>
      </div>
      <div className="rounded-xl bg-emerald-50 p-3 text-center">
        <dt className="text-xs text-stone-500">단어 보너스</dt>
        <dd className="mt-1 text-xl font-extrabold text-emerald-800">
          +{evaluation.wordBonus ?? 0}
        </dd>
      </div>
      <div className="rounded-xl bg-orange-50 p-3 text-center">
        <dt className="text-xs text-stone-500">문장 에너지</dt>
        <dd className="mt-1 text-xl font-extrabold text-orange-800">
          {evaluation.gameBaseAttack ?? 0}
        </dd>
      </div>
    </dl>
  );
}

export function SentenceEvaluationDetails({ evaluation }: SentenceEvaluationResultProps) {
  if (!evaluation.scores) return null;

  const scoreItems = [
    ['완결성', evaluation.scores.completeness],
    ['독창성', evaluation.scores.originality],
    ['표현력', evaluation.scores.expression],
    ['심상', evaluation.scores.imagery],
    ['정서', evaluation.scores.emotionalImpact],
  ];

  return (
    <div aria-label="세부 평가 결과">
      <dl className="mt-4 grid grid-cols-5 gap-1.5">
        {scoreItems.map(([label, score]) => (
          <div key={label} className="rounded-lg bg-stone-50 p-2 text-center">
            <dt className="text-[11px] text-stone-500">{label}</dt>
            <dd className="mt-1 text-sm font-bold text-stone-800">{score}</dd>
          </div>
        ))}
      </dl>

      {evaluation.moods.length > 0 && (
        <div className="mt-4 flex flex-wrap gap-2" aria-label="감지된 분위기">
          {evaluation.moods.map((mood) => (
            <span
              key={mood.code}
              className="rounded-full bg-violet-100 px-3 py-1.5 text-xs font-semibold text-violet-800"
            >
              {getMoodLabel(mood.code)} {Math.round(mood.strength * 100)}%
            </span>
          ))}
        </div>
      )}

      {evaluation.usedDailyWords.length > 0 && (
        <p className="mt-4 text-sm text-emerald-800">
          사용한 오늘의 단어:{' '}
          <strong>{evaluation.usedDailyWords.map(({ word }) => word).join(', ')}</strong>
        </p>
      )}

      {evaluation.overallFeedback && (
        <p className="mt-4 rounded-xl bg-stone-50 p-4 text-sm leading-6 text-stone-700">
          {evaluation.overallFeedback}
        </p>
      )}
    </div>
  );
}

export function SentenceEvaluationResult({ evaluation }: SentenceEvaluationResultProps) {
  if (evaluation.evaluationStatus === 'EVALUATING') {
    return <SentenceEvaluationLoading />;
  }

  if (evaluation.evaluationStatus === 'REJECTED') {
    return (
      <section role="alert" className="rounded-2xl border border-red-200 bg-red-50 p-5">
        <h2 className="font-bold text-red-900">AI 평가가 거부됐어요</h2>
        <p className="mt-2 text-sm leading-6 text-red-800">
          {evaluation.safety?.reason ??
            '일부 표현이 서비스 이용 기준에 맞지 않아 평가를 진행하지 못했어요. 문장을 수정한 뒤 다시 시도해 주세요.'}
        </p>
      </section>
    );
  }

  if (evaluation.evaluationStatus === 'EVALUATION_FAILED') {
    return (
      <section role="alert" className="rounded-2xl border border-orange-200 bg-orange-50 p-5">
        <h2 className="font-bold text-orange-900">평가를 완료하지 못했어요</h2>
        <p className="mt-2 text-sm text-orange-800">
          문장은 저장되어 있으며 다시 요청할 수 있어요.
        </p>
      </section>
    );
  }

  if (evaluation.evaluationStatus !== 'EVALUATED' || !evaluation.scores) {
    return null;
  }

  return (
    <SentenceEvaluationResultCard>
      <SentenceEvaluationScoreSummary evaluation={evaluation} />
      <SentenceEvaluationDetails evaluation={evaluation} />
    </SentenceEvaluationResultCard>
  );
}
