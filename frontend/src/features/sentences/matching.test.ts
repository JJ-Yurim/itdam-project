import { describe, expect, it } from 'vitest';

import type { DailyWord } from '@/features/daily-words';

import { getMatchedDailyWordIds } from './matching';

const dailyWords: DailyWord[] = [
  { position: 1, wordId: 1, word: '햇빛', partOfSpeech: '명사', definition: '', example: '' },
  { position: 2, wordId: 2, word: '골목', partOfSpeech: '명사', definition: '', example: '' },
  { position: 3, wordId: 3, word: '고요', partOfSpeech: '명사', definition: '', example: '' },
];

describe('getMatchedDailyWordIds', () => {
  it('입력 문장에 포함된 오늘의 단어 ID를 반환한다', () => {
    expect([...getMatchedDailyWordIds('고요한 골목에 햇빛이 번졌다.', dailyWords)]).toEqual([
      1, 2, 3,
    ]);
  });

  it('포함되지 않은 단어는 사용한 것으로 처리하지 않는다', () => {
    expect([...getMatchedDailyWordIds('비가 내린다.', dailyWords)]).toEqual([]);
  });
});
