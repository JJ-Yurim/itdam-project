// 카드 개봉 상태는 서버 계약이 없어 localStorage에만 저장한다(기기 간 미공유는 팀 합의).
// 홈과 개인 문장 생성 화면이 같은 저장소를 공유한다.

const STORAGE_PREFIX = 'revealed-words';

// 같은 브라우저에서 계정을 바꿔도 개봉 기록이 섞이지 않게 사용자 식별자를 키에 포함한다.
export type RevealOwner = number | 'guest';

function storageKey(owner: RevealOwner, serviceDate: string): string {
  return `${STORAGE_PREFIX}:${owner}:${serviceDate}`;
}

export function loadRevealedWordIds(owner: RevealOwner, serviceDate: string): Set<number> {
  try {
    const raw = localStorage.getItem(storageKey(owner, serviceDate));

    if (!raw) {
      return new Set();
    }

    const parsed: unknown = JSON.parse(raw);

    if (!Array.isArray(parsed)) {
      return new Set();
    }

    return new Set(parsed.filter((value): value is number => typeof value === 'number'));
  } catch {
    return new Set();
  }
}

export function saveRevealedWordIds(
  owner: RevealOwner,
  serviceDate: string,
  wordIds: Set<number>,
): void {
  try {
    localStorage.setItem(storageKey(owner, serviceDate), JSON.stringify([...wordIds]));
  } catch {
    // 저장 실패(용량 초과 등)는 치명적이지 않으므로 무시한다.
  }
}

// 서비스 날짜가 바뀌면 지난 날짜의 개봉 기록은 쓸모가 없으므로 정리한다.
export function cleanupPastRevealedWords(currentServiceDate: string): void {
  try {
    const staleKeys: string[] = [];

    for (let index = 0; index < localStorage.length; index += 1) {
      const key = localStorage.key(index);

      if (key?.startsWith(`${STORAGE_PREFIX}:`) && !key.endsWith(`:${currentServiceDate}`)) {
        staleKeys.push(key);
      }
    }

    staleKeys.forEach((key) => localStorage.removeItem(key));
  } catch {
    // 정리 실패는 동작에 영향이 없으므로 무시한다.
  }
}
