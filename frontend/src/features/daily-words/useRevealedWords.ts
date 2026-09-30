import { useCallback, useEffect, useState } from 'react';

import {
  cleanupPastRevealedWords,
  loadRevealedWordIds,
  saveRevealedWordIds,
} from './revealedWords';

import type { RevealOwner } from './revealedWords';

// 오늘의 단어 카드 개봉 상태를 화면 간 공유한다.
// owner나 serviceDate가 아직 없으면(조회 전) 빈 상태로 동작한다.
export function useRevealedWords(owner: RevealOwner | undefined, serviceDate: string | undefined) {
  const storageScope =
    owner !== undefined && serviceDate !== undefined ? `${owner}:${serviceDate}` : null;

  const [loadedScope, setLoadedScope] = useState<string | null>(null);
  const [revealedWordIds, setRevealedWordIds] = useState<Set<number>>(new Set());

  // 사용자·날짜가 바뀌면 해당 저장소의 개봉 상태로 갈아끼운다.
  // 이펙트 대신 렌더 중 상태 보정 패턴을 사용해 화면 깜빡임 없이 전환한다.
  if (storageScope !== loadedScope) {
    setLoadedScope(storageScope);
    setRevealedWordIds(
      storageScope === null
        ? new Set()
        : loadRevealedWordIds(owner as RevealOwner, serviceDate as string),
    );
  }

  // 지난 날짜의 개봉 기록 정리는 외부 저장소만 다루므로 이펙트에서 수행한다.
  useEffect(() => {
    if (serviceDate !== undefined) {
      cleanupPastRevealedWords(serviceDate);
    }
  }, [serviceDate]);

  const revealWords = useCallback(
    (wordIds: number[]) => {
      if (owner === undefined || serviceDate === undefined || wordIds.length === 0) {
        return;
      }

      setRevealedWordIds((current) => {
        if (wordIds.every((wordId) => current.has(wordId))) {
          return current;
        }

        const next = new Set(current);
        wordIds.forEach((wordId) => next.add(wordId));
        saveRevealedWordIds(owner, serviceDate, next);
        return next;
      });
    },
    [owner, serviceDate],
  );

  const revealWord = useCallback((wordId: number) => revealWords([wordId]), [revealWords]);

  return { revealedWordIds, revealWord, revealWords };
}
