import type { ReactNode } from 'react';

import type { DailyWord } from '@/features/daily-words';

interface MatchedSentencePreviewProps {
  content: string;
  dailyWords: DailyWord[];
  matchedWordIds: Set<number>;
}

function escapeRegExp(value: string) {
  return value.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
}

export function MatchedSentencePreview({
  content,
  dailyWords,
  matchedWordIds,
}: MatchedSentencePreviewProps) {
  if (!content) {
    return null;
  }

  const matchedWords = dailyWords
    .filter(({ wordId }) => matchedWordIds.has(wordId))
    .map(({ word }) => word)
    .sort((first, second) => second.length - first.length);

  if (matchedWords.length === 0) {
    return (
      <p className="whitespace-pre-wrap break-words text-sm leading-6 text-stone-600">{content}</p>
    );
  }

  const matchedWordSet = new Set(matchedWords);
  const pattern = new RegExp(`(${matchedWords.map(escapeRegExp).join('|')})`, 'gu');
  const parts: ReactNode[] = content.split(pattern).map((part, index) =>
    matchedWordSet.has(part) ? (
      <mark
        key={`${part}-${index}`}
        className="rounded bg-violet-200 px-0.5 font-semibold text-violet-950"
      >
        {part}
      </mark>
    ) : (
      part
    ),
  );

  return (
    <p className="whitespace-pre-wrap break-words text-sm leading-6 text-stone-700">{parts}</p>
  );
}
