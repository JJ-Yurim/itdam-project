import { useMutation, useQueryClient } from '@tanstack/react-query';

import { submitBossSentence } from './api';
import { calculateBossProgressPercent } from './progress';
import { bossQueryKeys } from './queryKeys';
import type { BossRunResponse, BossSubmissionRequest, BossSubmissionResponse } from './types';

export function useBossSubmissionMutation(bossRunId: number) {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (request: BossSubmissionRequest) => submitBossSentence(bossRunId, request),
    onSuccess: (result) => {
      // 내 제출은 재조회를 기다리지 않고 응답의 HP를 즉시 캐시에 반영한다. 응답과 SSE 이벤트
      // 모두 증분이 아니라 절대값을 담으므로, 이 패치와 곧 도착할 SSE 이벤트가 겹쳐도 같은
      // 값으로 수렴할 뿐 데미지가 두 번 적용되어 보일 수 없다.
      applySubmissionToDetail(result);
      void queryClient.invalidateQueries({
        queryKey: bossQueryKeys.detail(bossRunId),
        exact: true,
      });
      // SSE 리더보드 스냅숏은 상위 N명뿐이라 목록 밖의 내 순위(me)를 갱신하지 못한다.
      // 내 순위가 바뀌는 계기는 내 제출이므로 여기서 다시 가져온다.
      void queryClient.invalidateQueries({
        queryKey: bossQueryKeys.leaderboard(bossRunId),
        exact: true,
      });
      void queryClient.invalidateQueries({ queryKey: bossQueryKeys.lists() });
      void queryClient.invalidateQueries({ queryKey: bossQueryKeys.myRunLists() });
      void queryClient.invalidateQueries({
        queryKey: bossQueryKeys.eligibleSentenceLists(bossRunId),
      });
    },
  });

  function applySubmissionToDetail(result: BossSubmissionResponse) {
    queryClient.setQueryData<BossRunResponse>(bossQueryKeys.detail(bossRunId), (current) => {
      // 페이즈가 넘어갔거나 회차가 끝났으면 이벤트에 없는 값(새 페이즈 이미지·점수 기준)이
      // 바뀌므로 패치하지 않는다. 위의 무효화가 전체를 다시 가져온다.
      if (
        !current ||
        result.phase.phaseNo !== current.currentPhase.phaseNo ||
        result.bossRunStatus !== 'ACTIVE'
      ) {
        return current;
      }
      const maxHp = current.currentPhase.maxHp;
      return {
        ...current,
        currentPhase: {
          ...current.currentPhase,
          remainingHp: result.phase.remainingHp,
          progressPercent: calculateBossProgressPercent(maxHp, result.phase.remainingHp),
        },
      };
    });
  }
}
