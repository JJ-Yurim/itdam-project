import { beforeEach, describe, expect, it } from 'vitest';

import {
  cleanupPastRevealedWords,
  loadRevealedWordIds,
  saveRevealedWordIds,
} from './revealedWords';

describe('revealedWords', () => {
  beforeEach(() => {
    localStorage.clear();
  });

  it('저장한 개봉 상태를 같은 사용자·날짜 키로 다시 불러온다', () => {
    saveRevealedWordIds(1, '2026-08-04', new Set([4, 7]));

    expect(loadRevealedWordIds(1, '2026-08-04')).toEqual(new Set([4, 7]));
  });

  it('사용자가 다르면 개봉 상태가 분리된다', () => {
    saveRevealedWordIds(1, '2026-08-04', new Set([4]));

    expect(loadRevealedWordIds(2, '2026-08-04')).toEqual(new Set());
    expect(loadRevealedWordIds('guest', '2026-08-04')).toEqual(new Set());
  });

  it('날짜가 바뀌면 새 단어팩은 빈 상태에서 시작한다', () => {
    saveRevealedWordIds(1, '2026-08-04', new Set([4]));

    expect(loadRevealedWordIds(1, '2026-08-05')).toEqual(new Set());
  });

  it('지난 날짜의 개봉 기록을 정리하고 오늘 기록은 남긴다', () => {
    saveRevealedWordIds(1, '2026-08-03', new Set([1]));
    saveRevealedWordIds(2, '2026-08-02', new Set([2]));
    saveRevealedWordIds(1, '2026-08-04', new Set([4]));

    cleanupPastRevealedWords('2026-08-04');

    expect(loadRevealedWordIds(1, '2026-08-03')).toEqual(new Set());
    expect(loadRevealedWordIds(2, '2026-08-02')).toEqual(new Set());
    expect(loadRevealedWordIds(1, '2026-08-04')).toEqual(new Set([4]));
  });

  it('저장 값이 깨져 있으면 빈 상태로 복구한다', () => {
    localStorage.setItem('revealed-words:1:2026-08-04', '{broken');

    expect(loadRevealedWordIds(1, '2026-08-04')).toEqual(new Set());
  });
});
