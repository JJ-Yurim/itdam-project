import { useEffect, useState } from 'react';

import { useQueryClient } from '@tanstack/react-query';

import { bossQueryKeys } from './queryKeys';
import { calculateBossProgressPercent } from './progress';
import { useBossRunStreamFactory } from './stream';

import type { BossRunLeaderboardStreamEvent, BossRunStreamEvent } from './stream';
import type { BossRunLeaderboardResponse, BossRunResponse } from './types';

/** 이 횟수만큼 연달아 연결에 실패하면 실시간을 포기하고 폴링으로 강등한다. */
const FALLBACK_AFTER_CONSECUTIVE_ERRORS = 3;

export interface BossRunHit {
  damage: number;
  /** 페이즈 최소·권장 점수와 같은 단위로 비교하는 AI 기본 점수. */
  baseScore: number;
  /** 같은 데미지가 연속으로 와도 연출이 다시 실행되도록 이벤트마다 달라지는 키. */
  key: number;
}

export interface BossRunStreamState {
  /** false면 연결이 반복 실패해 폴링으로 강등된 상태다. 호출자는 refetchInterval을 켠다. */
  isRealtime: boolean;
  /** 마지막으로 도착한 타격. 데미지 연출용이며 스냅숏(데미지 0)은 제외한다. */
  lastHit: BossRunHit | null;
}

/**
 * 보스 회차 SSE를 구독해 상세 쿼리 캐시를 실시간으로 갱신한다.
 *
 * 이벤트의 HP는 증분이 아니라 절대값이므로 캐시에 몇 번을 반영해도 같은 값으로 수렴한다.
 * 내 제출의 낙관적 반영(제출 응답의 절대 HP)과 SSE 이벤트가 겹쳐도 중복 적용이 없는 이유다.
 *
 * 다음 세 경우는 패치 대신 쿼리 무효화로 서버와 재동기화한다.
 * - 시퀀스 틈: 이벤트가 유실됐다. 그 사이의 변화를 알 수 없다.
 * - 페이즈 전환·회차 종료: 새 페이즈의 이미지·점수 기준 등 이벤트에 없는 값이 바뀐다.
 * - 재연결: 끊긴 동안의 유실은 시퀀스로도 셀 수 없다.
 */
export function useBossRunStream(bossRunId: number, enabled: boolean): BossRunStreamState {
  const queryClient = useQueryClient();
  const createStream = useBossRunStreamFactory();
  const [isRealtime, setIsRealtime] = useState(true);
  const [lastHit, setLastHit] = useState<BossRunHit | null>(null);

  useEffect(() => {
    if (!enabled || bossRunId <= 0) {
      return;
    }

    let closed = false;
    let lastSequence: number | null = null;
    let consecutiveErrors = 0;
    // 첫 open은 재연결이 아니므로 재동기화를 건너뛴다.
    let hasOpenedBefore = false;
    // 팩토리가 동기적으로 핸들러를 부를 수 있으므로, connection 대입 전에도 닫기 요청을 놓치지 않게 한다.
    let connection: ReturnType<typeof createStream> = null;

    const closeConnection = () => {
      closed = true;
      connection?.close();
    };

    const invalidateDetail = () => {
      void queryClient.invalidateQueries({
        queryKey: bossQueryKeys.detail(bossRunId),
        exact: true,
      });
    };

    const invalidatePhaseDependentData = () => {
      invalidateDetail();
      void queryClient.invalidateQueries({
        queryKey: bossQueryKeys.eligibleSentenceLists(bossRunId),
      });
    };

    const applyEvent = (event: BossRunStreamEvent) => {
      queryClient.setQueryData<BossRunResponse>(bossQueryKeys.detail(bossRunId), (current) => {
        if (!current) {
          return current;
        }
        const phase = current.currentPhase;
        return {
          ...current,
          accumulatedDamage: event.accumulatedDamage,
          currentPhase: {
            ...phase,
            remainingHp: event.remainingHp,
            progressPercent: calculateBossProgressPercent(event.maxHp, event.remainingHp),
          },
        };
      });
    };

    const applyLeaderboard = (event: BossRunLeaderboardStreamEvent) => {
      queryClient.setQueryData<BossRunLeaderboardResponse>(
        bossQueryKeys.leaderboard(bossRunId),
        (current) => {
          // 캐시가 없으면 리더보드 화면이 아직 조회 전이다. 첫 조회가 더 신선한 값을
          // 가져오므로 여기서 심지 않는다(me를 모른 채 심으면 내 순위가 비어 보인다).
          if (!current) {
            return current;
          }
          // 스냅숏은 상위 N명뿐이라 me는 갱신할 수 없다. 목록에 내가 있으면 화면이
          // 목록 쪽 값을 우선 표시하고, 내 순위 자체는 내 제출 성공 시 무효화로 갱신된다.
          return { ...current, entries: event.entries };
        },
      );
    };

    connection = createStream(bossRunId, {
      onOpen: () => {
        consecutiveErrors = 0;
        // 일시 장애로 강등됐다가 재연결에 성공하면 실시간으로 복귀한다. 복귀하지 않으면
        // SSE 구독과 폴링이 동시에 돌면서 배지는 계속 강등 상태로 남는다.
        setIsRealtime(true);
        if (hasOpenedBefore) {
          // 끊긴 동안의 이벤트는 시퀀스로도 셀 수 없으므로 서버 상태로 재동기화한다.
          // 기준 시퀀스는 재연결 직후 도착하는 스냅숏이 다시 알려준다.
          lastSequence = null;
          invalidatePhaseDependentData();
        }
        hasOpenedBefore = true;
      },
      onError: (permanentlyClosed) => {
        consecutiveErrors += 1;
        // 영구 종료(비200 응답 등)는 error가 한 번만 오므로 횟수를 세지 않고 즉시 강등한다.
        // 세션 만료나 배포 중 502가 이 경로다.
        if (
          (permanentlyClosed || consecutiveErrors >= FALLBACK_AFTER_CONSECUTIVE_ERRORS) &&
          !closed
        ) {
          closeConnection();
          setIsRealtime(false);
        }
      },
      onLeaderboard: (event) => {
        if (event.bossRunId !== bossRunId) {
          return;
        }
        applyLeaderboard(event);
      },
      onEvent: (sequence, event) => {
        if (event.bossRunId !== bossRunId) {
          return;
        }
        // 이미 반영한 시점 이전의 이벤트(중복·역행)는 버린다.
        if (lastSequence !== null && sequence <= lastSequence) {
          return;
        }
        const gapDetected = lastSequence !== null && sequence > lastSequence + 1;
        lastSequence = sequence;

        if (event.appliedDamage > 0) {
          setLastHit({ damage: event.appliedDamage, baseScore: event.baseScore, key: sequence });
        }

        const current = queryClient.getQueryData<BossRunResponse>(bossQueryKeys.detail(bossRunId));
        const phaseChanged =
          current !== undefined && current.currentPhase.phaseNo !== event.phaseNo;
        const runEnded = event.runStatus !== 'ACTIVE';
        // 페이즈를 끝낸 타격 이벤트는 "기존 페이즈·HP 0·ACTIVE"로 도착하고, 다음 페이즈의
        // 첫 타격이 나올 때까지 후속 이벤트가 없다. 이 이벤트를 패치로만 처리하면 처치한
        // 사람 외에는 화면이 HP 0에서 멈추므로, 페이즈 경계로 보고 서버와 재동기화한다.
        const phaseCleared = event.remainingHp <= 0;

        // current가 없는 경우(최초 로딩보다 이벤트가 먼저 도착)도 재동기화로 넘긴다.
        // 그대로 두면 패치할 대상이 없어 이벤트가 흔적 없이 사라지는데 lastSequence만 전진한다.
        if (gapDetected || current === undefined || phaseChanged || runEnded || phaseCleared) {
          invalidatePhaseDependentData();
          return;
        }
        applyEvent(event);
      },
    });

    // SSE를 지원하지 않는 환경(EventSource 부재)이다. 화면은 폴링으로 계속 갱신되어야 한다.
    // 렌더 중 상태 변경을 피하려고 마이크로태스크로 미룬다(react-hooks/set-state-in-effect).
    if (connection === null) {
      queueMicrotask(() => setIsRealtime(false));
      return;
    }

    return () => {
      if (!closed) {
        closeConnection();
      }
    };
  }, [bossRunId, enabled, createStream, queryClient]);

  return { isRealtime, lastHit };
}
