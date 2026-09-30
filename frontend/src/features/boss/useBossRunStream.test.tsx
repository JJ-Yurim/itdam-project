import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { act, renderHook } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';

import { bossQueryKeys } from './queryKeys';
import { BossRunStreamContext } from './stream';
import { useBossRunStream } from './useBossRunStream';

import type {
  BossRunLeaderboardStreamEvent,
  BossRunStreamEvent,
  BossRunStreamHandlers,
} from './stream';
import type { BossRunLeaderboardResponse, BossRunResponse } from './types';
import type { ReactNode } from 'react';

const BOSS_RUN_ID = 7;

function detailFixture(overrides: Partial<BossRunResponse['currentPhase']> = {}): BossRunResponse {
  return {
    bossRunId: BOSS_RUN_ID,
    bossId: 1,
    bossName: '테스트 보스',
    bossDescription: '',
    status: 'ACTIVE',
    startedAt: '2026-08-01T00:00:00Z',
    endsAt: '2026-08-31T00:00:00Z',
    clearedAt: null,
    accumulatedDamage: 0,
    currentPhase: {
      phaseId: 11,
      phaseNo: 1,
      maxHp: 1_000,
      remainingHp: 1_000,
      progressPercent: 0,
      hardMinScore: 0,
      recommendedScore: 60,
      damageMultiplier: 1,
      imageUrl: null,
      status: 'ACTIVE',
      ...overrides,
    },
    moods: [],
    mySubmissionCount: 0,
    remainingSubmissionCount: 3,
    mySubmissions: [],
  };
}

function hpEvent(overrides: Partial<BossRunStreamEvent> = {}): BossRunStreamEvent {
  return {
    bossRunId: BOSS_RUN_ID,
    phaseNo: 1,
    remainingHp: 890,
    maxHp: 1_000,
    accumulatedDamage: 110,
    appliedDamage: 110,
    baseScore: 84,
    runStatus: 'ACTIVE',
    ...overrides,
  };
}

function leaderboardFixture(): BossRunLeaderboardResponse {
  return {
    bossRunId: BOSS_RUN_ID,
    entries: [
      { rank: 1, userId: 11, nickname: '문장수집가', totalDamage: 380 },
      { rank: 2, userId: 12, nickname: '달빛서기', totalDamage: 240 },
    ],
    me: { rank: 5, totalDamage: 57 },
  };
}

/** 테스트가 이벤트를 직접 밀어 넣는 페이크 스트림. */
function setUpStream(options: { seedDetail?: boolean; seedLeaderboard?: boolean } = {}) {
  const { seedDetail = true, seedLeaderboard = false } = options;
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  if (seedDetail) {
    queryClient.setQueryData(bossQueryKeys.detail(BOSS_RUN_ID), detailFixture());
  }
  if (seedLeaderboard) {
    queryClient.setQueryData(bossQueryKeys.leaderboard(BOSS_RUN_ID), leaderboardFixture());
  }

  // 캐시가 비어 있는 경우에는 쿼리 상태 자체가 없어 isInvalidated로 관측할 수 없으므로
  // 무효화 호출을 스파이로 센다.
  const invalidateSpy = vi.spyOn(queryClient, 'invalidateQueries');

  let handlers: BossRunStreamHandlers | null = null;
  let closeCount = 0;
  const factory = (_bossRunId: number, streamHandlers: BossRunStreamHandlers) => {
    handlers = streamHandlers;
    return {
      close: () => {
        closeCount += 1;
      },
    };
  };

  const wrapper = ({ children }: { children: ReactNode }) => (
    <QueryClientProvider client={queryClient}>
      <BossRunStreamContext.Provider value={factory}>{children}</BossRunStreamContext.Provider>
    </QueryClientProvider>
  );

  const rendered = renderHook(() => useBossRunStream(BOSS_RUN_ID, true), { wrapper });

  return {
    queryClient,
    rendered,
    getCloseCount: () => closeCount,
    push: (sequence: number, event: BossRunStreamEvent) =>
      act(() => handlers?.onEvent(sequence, event)),
    pushLeaderboard: (event: BossRunLeaderboardStreamEvent) =>
      act(() => handlers?.onLeaderboard(event)),
    open: () => act(() => handlers?.onOpen()),
    fail: (permanentlyClosed = false) => act(() => handlers?.onError(permanentlyClosed)),
    detail: () => queryClient.getQueryData<BossRunResponse>(bossQueryKeys.detail(BOSS_RUN_ID)),
    leaderboard: () =>
      queryClient.getQueryData<BossRunLeaderboardResponse>(bossQueryKeys.leaderboard(BOSS_RUN_ID)),
    invalidatedCount: () => invalidateSpy.mock.calls.length,
  };
}

describe('useBossRunStream', () => {
  it('hp 이벤트를 상세 캐시에 절대값으로 반영한다', () => {
    const stream = setUpStream();
    stream.open();

    stream.push(1, hpEvent({ remainingHp: 890 }));

    expect(stream.detail()?.currentPhase.remainingHp).toBe(890);
    expect(stream.detail()?.currentPhase.progressPercent).toBe(11);
    expect(stream.detail()?.accumulatedDamage).toBe(110);
    expect(stream.rendered.result.current.lastHit).toEqual({
      damage: 110,
      baseScore: 84,
      key: 1,
    });
  });

  it('이미 반영한 시퀀스 이전의 이벤트는 버린다', () => {
    const stream = setUpStream();
    stream.open();
    stream.push(2, hpEvent({ remainingHp: 780, accumulatedDamage: 220 }));

    // 지연 도착한 과거 이벤트가 최신 HP를 되돌리면 안 된다.
    stream.push(1, hpEvent({ remainingHp: 890 }));

    expect(stream.detail()?.currentPhase.remainingHp).toBe(780);
    expect(stream.detail()?.accumulatedDamage).toBe(220);
  });

  it('시퀀스에 틈이 생기면 패치 대신 서버 재동기화를 건다', () => {
    const stream = setUpStream();
    stream.open();
    stream.push(1, hpEvent({ remainingHp: 890 }));

    stream.push(3, hpEvent({ remainingHp: 670 }));

    // 유실된 2번 이벤트의 변화를 알 수 없으므로 캐시 패치가 아니라 무효화여야 한다.
    expect(stream.invalidatedCount()).toBe(2);
    expect(stream.detail()?.currentPhase.remainingHp).toBe(890);
  });

  it('페이즈가 넘어간 이벤트는 재동기화를 건다', () => {
    const stream = setUpStream();
    stream.open();

    stream.push(1, hpEvent({ phaseNo: 2, remainingHp: 2_000, maxHp: 2_000, appliedDamage: 0 }));

    // 새 페이즈의 이미지·점수 기준은 이벤트에 없으므로 전체를 다시 가져와야 한다.
    expect(stream.invalidatedCount()).toBe(2);
  });

  it('페이즈를 끝낸 타격(HP 0·ACTIVE)은 패치 대신 재동기화를 건다', () => {
    const stream = setUpStream();
    stream.open();

    // 페이즈 킬 이벤트는 기존 페이즈 번호로 도착하고, 다음 페이즈 첫 타격까지 후속
    // 이벤트가 없다. 패치로만 처리하면 처치자 외의 화면이 HP 0에서 멈춘다.
    stream.push(1, hpEvent({ remainingHp: 0, appliedDamage: 890 }));

    expect(stream.invalidatedCount()).toBe(2);
  });

  it('회차가 끝난 이벤트는 재동기화를 건다', () => {
    const stream = setUpStream();
    stream.open();

    stream.push(1, hpEvent({ remainingHp: 0, runStatus: 'CLEARED' }));

    expect(stream.invalidatedCount()).toBe(2);
  });

  it('재연결되면 끊긴 동안의 유실을 재동기화한다', () => {
    const stream = setUpStream();
    stream.open();
    stream.push(1, hpEvent({ remainingHp: 890 }));

    stream.fail();
    stream.open();

    expect(stream.invalidatedCount()).toBe(2);
  });

  it('연결이 반복 실패하면 닫고 폴링으로 강등한다', () => {
    const stream = setUpStream();
    stream.open();
    expect(stream.rendered.result.current.isRealtime).toBe(true);

    stream.fail();
    stream.fail();
    stream.fail();

    expect(stream.rendered.result.current.isRealtime).toBe(false);
    expect(stream.getCloseCount()).toBe(1);
  });

  it('영구 종료된 연결은 횟수를 세지 않고 즉시 폴링으로 강등한다', () => {
    // 비200 응답·잘못된 Content-Type이면 EventSource는 재연결 없이 error를 한 번만 낸다.
    // 반복 횟수만 세면 이 경우 강등이 영영 일어나지 않아, 세션 만료·배포 중 502에서
    // 화면이 실시간 배지를 띄운 채 멈춘다.
    const stream = setUpStream();
    stream.open();

    stream.fail(true);

    expect(stream.rendered.result.current.isRealtime).toBe(false);
    expect(stream.getCloseCount()).toBe(1);
  });

  it('일시 실패 뒤 재연결에 성공하면 실시간으로 복귀하고 실패 횟수가 초기화된다', () => {
    const stream = setUpStream();
    stream.open();
    stream.fail();
    stream.fail();

    stream.open();
    stream.fail();
    stream.fail();

    // 재연결이 실패 횟수를 초기화하지 않으면 누적 4회로 잘못 강등된다.
    expect(stream.rendered.result.current.isRealtime).toBe(true);
    expect(stream.getCloseCount()).toBe(0);
  });

  it('상세 캐시가 아직 없으면 패치 대신 재동기화를 건다', () => {
    // 최초 로딩보다 이벤트가 먼저 도착한 경우다. 그대로 두면 패치 대상이 없어 이벤트가
    // 사라지는데 시퀀스 기준만 전진한다.
    const stream = setUpStream({ seedDetail: false });
    stream.open();

    stream.push(1, hpEvent({ remainingHp: 890 }));

    expect(stream.invalidatedCount()).toBe(2);
  });

  it('언마운트하면 연결을 닫는다', () => {
    const stream = setUpStream();
    stream.open();

    stream.rendered.unmount();

    expect(stream.getCloseCount()).toBe(1);
  });

  it('다른 회차의 이벤트는 무시한다', () => {
    const stream = setUpStream();
    stream.open();

    stream.push(1, hpEvent({ bossRunId: 999, remainingHp: 1 }));

    expect(stream.detail()?.currentPhase.remainingHp).toBe(1_000);
  });

  it('leaderboard 스냅숏으로 순위 캐시의 목록을 대체하고 내 순위는 유지한다', () => {
    const stream = setUpStream({ seedLeaderboard: true });
    stream.open();

    stream.pushLeaderboard({
      bossRunId: BOSS_RUN_ID,
      entries: [
        { rank: 1, userId: 12, nickname: '달빛서기', totalDamage: 460 },
        { rank: 2, userId: 11, nickname: '문장수집가', totalDamage: 380 },
      ],
    });

    const leaderboard = stream.leaderboard();
    // 스냅숏은 최신 전체이므로 병합 없이 대체한다 — 순위 역전이 그대로 반영되어야 한다.
    expect(leaderboard?.entries.map((entry) => entry.userId)).toEqual([12, 11]);
    // 스냅숏에는 상위 N명뿐이라 me를 알 수 없다. 지우면 목록 밖 사용자의 내 순위가 사라진다.
    expect(leaderboard?.me).toEqual({ rank: 5, totalDamage: 57 });
  });

  it('순위 캐시가 아직 없으면 leaderboard 스냅숏을 심지 않는다', () => {
    // 첫 조회 전이다. me 없이 심으면 첫 화면에서 내 순위가 비어 보인다. 첫 조회가 채운다.
    const stream = setUpStream();
    stream.open();

    stream.pushLeaderboard({
      bossRunId: BOSS_RUN_ID,
      entries: [{ rank: 1, userId: 11, nickname: '문장수집가', totalDamage: 380 }],
    });

    expect(stream.leaderboard()).toBeUndefined();
  });

  it('다른 회차의 leaderboard 스냅숏은 무시한다', () => {
    const stream = setUpStream({ seedLeaderboard: true });
    stream.open();

    stream.pushLeaderboard({
      bossRunId: 999,
      entries: [{ rank: 1, userId: 99, nickname: '다른회차', totalDamage: 1 }],
    });

    expect(stream.leaderboard()?.entries.map((entry) => entry.userId)).toEqual([11, 12]);
  });
});
