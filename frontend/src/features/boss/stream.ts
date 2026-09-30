import { createContext, useContext } from 'react';

import type { BossRunLeaderboardEntry } from './types';

/** 백엔드 SSE `hp` 이벤트의 data 본문. 시퀀스는 본문이 아니라 SSE 이벤트 id로 온다. */
export interface BossRunStreamEvent {
  bossRunId: number;
  phaseNo: number;
  remainingHp: number;
  maxHp: number;
  /** 회차에 누적된 전체 피해의 절대값. */
  accumulatedDamage: number;
  /** 이 이벤트를 만든 제출의 데미지. 구독 직후 스냅숏에서는 0이다. */
  appliedDamage: number;
  /** 일반/강한 공격 연출을 판정하는 AI 기본 점수. */
  baseScore: number;
  runStatus: string;
}

/**
 * 백엔드 SSE `leaderboard` 이벤트의 data 본문. 병합 주기(약 1초)마다 오는 최신 스냅숏
 * 전체이므로 이전 상태와 병합하지 않고 그대로 대체한다. hp와 달리 SSE id(시퀀스)가 없다 —
 * 하나를 놓쳐도 다음 스냅숏이 전체를 대체하므로 유실 감지가 필요 없다.
 */
export interface BossRunLeaderboardStreamEvent {
  bossRunId: number;
  entries: BossRunLeaderboardEntry[];
}

export interface BossRunStreamHandlers {
  /** hp 이벤트 수신. sequence는 회차 단위로 1씩 증가하는 SSE 이벤트 id다. */
  onEvent: (sequence: number, event: BossRunStreamEvent) => void;
  /** leaderboard 스냅숏 수신. 시퀀스 검사 없이 최신 스냅숏으로 대체하면 된다. */
  onLeaderboard: (event: BossRunLeaderboardStreamEvent) => void;
  /** 연결 성공(재연결 포함). 끊긴 동안의 유실을 재동기화하는 신호로 쓴다. */
  onOpen: () => void;
  /**
   * 연결 실패. permanentlyClosed가 true면 EventSource가 재연결을 포기한 종료다
   * (비200 응답, 잘못된 Content-Type 등). 이때 error는 한 번만 오므로 호출자는
   * 반복 횟수를 세지 말고 즉시 강등해야 한다.
   */
  onError: (permanentlyClosed: boolean) => void;
}

export interface BossRunStreamConnection {
  close: () => void;
}

/**
 * SSE 연결을 여는 팩토리. 컨텍스트로 주입한다.
 *
 * 주입 가능해야 하는 이유: jsdom에는 EventSource가 없어 테스트가 실제 구현을 실행할 수
 * 없고, msw로도 무한 스트림 응답은 재현이 까다롭다. 테스트는 페이크 팩토리를 주입해
 * 이벤트를 직접 밀어 넣는다.
 *
 * SSE를 지원하지 않는 환경에서는 null을 반환한다. 호출자(훅)는 폴링으로 강등한다.
 */
export type BossRunStreamFactory = (
  bossRunId: number,
  handlers: BossRunStreamHandlers,
) => BossRunStreamConnection | null;

export const eventSourceBossRunStreamFactory: BossRunStreamFactory = (bossRunId, handlers) => {
  // jsdom과 일부 구형 브라우저에는 EventSource가 없다. 실시간 없이도 화면은 동작해야 한다.
  if (typeof EventSource === 'undefined') {
    return null;
  }
  // httpClient와 같은 베이스를 쓴다. ||인 이유: 빈 문자열로 정의된 환경 변수도 폴백해야 한다.
  // EventSource는 쿠키를 자동 전송하므로(withCredentials는 교차 오리진용) 세션 인증에 별도
  // 처리가 필요 없다.
  const base = import.meta.env.VITE_API_BASE_URL || '/api';
  const source = new EventSource(`${base}/v1/boss-runs/${bossRunId}/events`, {
    withCredentials: true,
  });

  source.addEventListener('open', handlers.onOpen);
  source.addEventListener('error', () => {
    // 비200 응답·잘못된 Content-Type 등은 스펙상 재연결 없이 CLOSED로 끝난다.
    handlers.onError(source.readyState === EventSource.CLOSED);
  });
  source.addEventListener('leaderboard', (message: MessageEvent<string>) => {
    let snapshot: BossRunLeaderboardStreamEvent;
    try {
      snapshot = JSON.parse(message.data) as BossRunLeaderboardStreamEvent;
    } catch {
      // 본문이 깨진 스냅숏은 버린다. 다음 스냅숏이 전체를 대체한다.
      return;
    }
    // 파싱 성공과 형태 보장은 다르다. entries가 배열이 아니면 캐시를 오염시키므로 버린다.
    // 소비자 콜백의 예외는 여기서 삼키지 않는다 — try 밖이라 흔적 없이 사라지지 않는다.
    if (typeof snapshot?.bossRunId !== 'number' || !Array.isArray(snapshot.entries)) {
      return;
    }
    handlers.onLeaderboard(snapshot);
  });
  source.addEventListener('hp', (message: MessageEvent<string>) => {
    // 빈 lastEventId는 Number('') === 0이라 스냅숏의 정당한 id 0과 구분되지 않으므로 먼저
    // 거른다. id가 없는 이벤트를 0으로 통과시키면 이후 모든 이벤트가 과거로 오인되어 버려진다.
    if (message.lastEventId.trim() === '') {
      return;
    }
    const sequence = Number(message.lastEventId);
    if (!Number.isInteger(sequence) || sequence < 0) {
      return;
    }
    try {
      handlers.onEvent(sequence, JSON.parse(message.data) as BossRunStreamEvent);
    } catch {
      // 본문이 깨진 이벤트는 버린다. 시퀀스 틈으로 감지되어 재동기화된다.
    }
  });

  return { close: () => source.close() };
};

export const BossRunStreamContext = createContext<BossRunStreamFactory>(
  eventSourceBossRunStreamFactory,
);

export function useBossRunStreamFactory(): BossRunStreamFactory {
  return useContext(BossRunStreamContext);
}
