import type { DailyWord } from '@/features/daily-words';

export function getMatchedDailyWordIds(content: string, dailyWords: DailyWord[]): Set<number> {
  const normalizedContent = content.normalize('NFC');

  return new Set(
    dailyWords
      .filter(({ word }) => normalizedContent.includes(word.normalize('NFC')))
      .map(({ wordId }) => wordId),
  );
}
