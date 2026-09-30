import { afterEach, describe, expect, it, vi } from 'vitest';

import { eventSourceBossRunStreamFactory } from './stream';

import type {
  BossRunLeaderboardStreamEvent,
  BossRunStreamEvent,
  BossRunStreamHandlers,
} from './stream';

/** jsdom에는 EventSource가 없으므로 파싱·오류 분기를 검증할 최소 대역을 전역에 심는다. */
class FakeEventSource {
  static readonly CONNECTING = 0;
  static readonly OPEN = 1;
  static readonly CLOSED = 2;
  static lastInstance: FakeEventSource | null = null;

  readonly url: string;
  readyState = FakeEventSource.CONNECTING;
  closeCount = 0;
  private readonly listeners = new Map<string, (event: unknown) => void>();

  constructor(url: string) {
    this.url = url;
    FakeEventSource.lastInstance = this;
  }

  addEventListener(type: string, listener: (event: unknown) => void) {
    this.listeners.set(type, listener);
  }

  close() {
    this.closeCount += 1;
  }

  emit(type: string, event: unknown) {
    this.listeners.get(type)?.(event);
  }
}

function setUpFactory() {
  vi.stubGlobal('EventSource', FakeEventSource);
  const received: { sequence: number; event: BossRunStreamEvent }[] = [];
  const leaderboards: BossRunLeaderboardStreamEvent[] = [];
  const errors: boolean[] = [];
  const handlers: BossRunStreamHandlers = {
    onEvent: (sequence, event) => received.push({ sequence, event }),
    onLeaderboard: (event) => leaderboards.push(event),
    onOpen: () => undefined,
    onError: (permanentlyClosed) => errors.push(permanentlyClosed),
  };
  const connection = eventSourceBossRunStreamFactory(7, handlers);
  const source = FakeEventSource.lastInstance;
  if (connection === null || source === null) {
    throw new Error('팩토리가 연결을 만들지 못했다.');
  }
  return { connection, source, received, leaderboards, errors };
}

function hpMessage(lastEventId: string, data: string) {
  return { lastEventId, data };
}

const eventBody = JSON.stringify({
  bossRunId: 7,
  phaseNo: 1,
  remainingHp: 890,
  maxHp: 1000,
  accumulatedDamage: 110,
  appliedDamage: 110,
  baseScore: 84,
  runStatus: 'ACTIVE',
});

afterEach(() => {
  FakeEventSource.lastInstance = null;
  vi.unstubAllGlobals();
});

describe('eventSourceBossRunStreamFactory', () => {
  it('EventSource가 없는 환경에서는 null을 반환한다', () => {
    // jsdom 기본 상태가 곧 미지원 환경이다.
    expect(
      eventSourceBossRunStreamFactory(7, {
        onEvent: () => undefined,
        onLeaderboard: () => undefined,
        onOpen: () => undefined,
        onError: () => undefined,
      }),
    ).toBeNull();
  });

  it('id가 0인 스냅숏 이벤트를 전달한다', () => {
    const { source, received } = setUpFactory();

    // 새 회차의 구독 스냅숏은 발급된 시퀀스가 없어 id가 정당하게 0이다. 버리면 기준
    // 시퀀스를 알 수 없다.
    source.emit('hp', hpMessage('0', eventBody));

    expect(received).toHaveLength(1);
    expect(received[0].sequence).toBe(0);
    expect(received[0].event.accumulatedDamage).toBe(110);
  });

  it('id가 비어 있는 이벤트는 버린다', () => {
    const { source, received } = setUpFactory();

    // Number('')는 0이라 스냅숏과 구분되지 않는다. 0으로 통과시키면 이후 모든 이벤트가
    // 과거로 오인되어 버려진다.
    source.emit('hp', hpMessage('', eventBody));
    source.emit('hp', hpMessage('   ', eventBody));
    source.emit('hp', hpMessage('abc', eventBody));
    source.emit('hp', hpMessage('1.5', eventBody));

    expect(received).toHaveLength(0);
  });

  it('본문이 깨진 이벤트는 버린다', () => {
    const { source, received } = setUpFactory();

    source.emit('hp', hpMessage('1', '{broken'));

    expect(received).toHaveLength(0);
  });

  it('영구 종료 여부를 readyState로 구분해 전달한다', () => {
    const { source, errors } = setUpFactory();

    source.readyState = FakeEventSource.CONNECTING;
    source.emit('error', {});
    source.readyState = FakeEventSource.CLOSED;
    source.emit('error', {});

    expect(errors).toEqual([false, true]);
  });

  it('leaderboard 이벤트를 id 검사 없이 전달한다', () => {
    const { source, leaderboards } = setUpFactory();
    const snapshot = {
      bossRunId: 7,
      entries: [{ rank: 1, userId: 11, nickname: '문장수집가', totalDamage: 380 }],
    };

    // 순위 스냅숏은 SSE id(시퀀스)를 싣지 않는다. hp처럼 id를 요구하면 전부 버려진다.
    source.emit('leaderboard', { lastEventId: '', data: JSON.stringify(snapshot) });

    expect(leaderboards).toEqual([snapshot]);
  });

  it('본문이 깨진 leaderboard 이벤트는 버린다', () => {
    const { source, leaderboards } = setUpFactory();

    source.emit('leaderboard', { lastEventId: '', data: '{broken' });

    expect(leaderboards).toHaveLength(0);
  });

  it('형태가 어긋난 leaderboard 이벤트는 전달하지 않는다', () => {
    const { source, leaderboards } = setUpFactory();

    // JSON으로는 유효하지만 스냅숏 형태가 아닌 본문. 그대로 통과시키면 entries 접근에서
    // 소비자가 깨지거나 캐시가 undefined로 오염된다.
    source.emit('leaderboard', { lastEventId: '', data: 'null' });
    source.emit('leaderboard', { lastEventId: '', data: '42' });
    source.emit('leaderboard', { lastEventId: '', data: JSON.stringify({ bossRunId: 7 }) });
    source.emit('leaderboard', {
      lastEventId: '',
      data: JSON.stringify({ bossRunId: '7', entries: [] }),
    });

    expect(leaderboards).toHaveLength(0);
  });

  it('소비자 콜백의 예외는 leaderboard 파싱 오류로 삼켜지지 않는다', () => {
    // 파싱과 전달이 같은 try에 있으면 setQueryData 경로의 버그가 흔적 없이 사라진다.
    vi.stubGlobal('EventSource', FakeEventSource);
    eventSourceBossRunStreamFactory(7, {
      onEvent: () => undefined,
      onLeaderboard: () => {
        throw new Error('consumer bug');
      },
      onOpen: () => undefined,
      onError: () => undefined,
    });
    const source = FakeEventSource.lastInstance;

    expect(() =>
      source?.emit('leaderboard', {
        lastEventId: '',
        data: JSON.stringify({ bossRunId: 7, entries: [] }),
      }),
    ).toThrowError('consumer bug');
  });

  it('close는 EventSource를 닫는다', () => {
    const { connection, source } = setUpFactory();

    connection.close();

    expect(source.closeCount).toBe(1);
  });
});
