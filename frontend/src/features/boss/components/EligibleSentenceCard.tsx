import type { ReactNode } from 'react';

import type { EligibleSentenceResponse } from '../types';

interface EligibleSentenceCardProps {
  sentence: EligibleSentenceResponse;
  isSelected: boolean;
  onToggle: (sentenceVersionId: number) => void;
  unavailableReason?: string;
  /** 문장 본문 아래에 붙는 부가 정보 슬롯 (예: 분위기 태그) */
  extra?: ReactNode;
}

export function EligibleSentenceCard({
  sentence,
  isSelected,
  onToggle,
  unavailableReason,
  extra,
}: EligibleSentenceCardProps) {
  // 프론트와 백엔드가 순차 배포되는 짧은 구간에는 새 필드가 없을 수 있다.
  // 그때도 카드가 깨지지 않도록 기존 값을 임시로 사용하고, 정상 응답에서는 항상 문장 에너지를 쓴다.
  const sentenceEnergy = Number.isFinite(sentence.sentenceEnergy)
    ? sentence.sentenceEnergy
    : Math.round(sentence.gameBaseAttack);
  const isUnavailable = unavailableReason !== undefined;

  return (
    <button
      type="button"
      aria-pressed={isSelected}
      disabled={isUnavailable}
      onClick={() => onToggle(sentence.sentenceVersionId)}
      className={[
        'w-full px-3 py-2.5 text-left transition lg:px-5 lg:py-4',
        'focus-visible:rounded-xl focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-violet-600',
        isUnavailable
          ? 'cursor-not-allowed rounded-xl bg-red-50 ring-1 ring-inset ring-red-200'
          : isSelected
            ? 'rounded-xl bg-violet-50 ring-2 ring-violet-200'
            : 'hover:bg-stone-50',
      ].join(' ')}
    >
      {unavailableReason && (
        <span className="mb-1.5 inline-flex rounded-full bg-red-100 px-2 py-0.5 text-[11px] font-bold text-red-700">
          {unavailableReason}
        </span>
      )}

      <p className="whitespace-pre-wrap break-words text-sm leading-6 text-stone-900 lg:text-lg lg:leading-8">
        {sentence.content}
      </p>

      {extra}

      <dl
        className={[
          'mt-2 flex flex-wrap items-center gap-x-4 gap-y-1 border-t pt-2',
          isUnavailable ? 'border-red-100' : 'border-stone-100',
        ].join(' ')}
      >
        <div className="flex items-baseline gap-1.5">
          <dt className="text-[11px] text-stone-500 lg:text-sm">기본 점수</dt>
          <dd className="text-sm font-bold text-stone-900 lg:text-base">{sentence.baseScore}</dd>
        </div>

        <div className="flex items-baseline gap-1.5">
          <dt className="text-[11px] text-emerald-700 lg:text-sm">단어 보너스</dt>
          <dd className="text-sm font-bold text-emerald-800 lg:text-base">+{sentence.wordBonus.toFixed(1)}</dd>
        </div>

        <div className="ml-auto flex items-baseline gap-1.5 rounded-lg bg-orange-50 px-2.5 py-1.5">
          <dt className="text-[11px] text-orange-700 lg:text-sm">문장 에너지</dt>
          <dd className="text-sm font-extrabold text-orange-800 lg:text-base">
            {sentenceEnergy.toLocaleString()}
          </dd>
        </div>
      </dl>
    </button>
  );
}
