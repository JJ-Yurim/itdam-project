import type { ReactNode } from 'react';

import type { EligibleSentenceResponse } from '../types';

import { EligibleSentenceCard } from './EligibleSentenceCard';

interface EligibleSentenceListProps {
  items: EligibleSentenceResponse[];
  selectedSentenceVersionId: number | null;
  onSelectionChange: (sentenceVersionId: number | null) => void;
  minimumScore?: number | null;
  /** 각 문장 카드 본문 아래에 붙는 부가 정보 렌더러 (예: 분위기 태그) */
  renderExtra?: (sentence: EligibleSentenceResponse) => ReactNode;
}

export function EligibleSentenceList({
  items,
  selectedSentenceVersionId,
  onSelectionChange,
  minimumScore,
  renderExtra,
}: EligibleSentenceListProps) {
  const handleToggle = (sentenceVersionId: number) => {
    const nextSentenceVersionId =
      selectedSentenceVersionId === sentenceVersionId ? null : sentenceVersionId;

    onSelectionChange(nextSentenceVersionId);
  };

  return (
    <ul
      aria-label="제출 가능한 문장 목록"
      className="divide-y divide-stone-200 rounded-2xl border border-stone-200 bg-white px-4 shadow-sm"
    >
      {items.map((sentence) => (
        <li key={sentence.sentenceVersionId}>
          <EligibleSentenceCard
            sentence={sentence}
            isSelected={selectedSentenceVersionId === sentence.sentenceVersionId}
            onToggle={handleToggle}
            unavailableReason={
              minimumScore != null && sentence.baseScore < minimumScore
                ? '최소 점수 미달 · 제출 불가'
                : undefined
            }
            extra={renderExtra?.(sentence)}
          />
        </li>
      ))}
    </ul>
  );
}
