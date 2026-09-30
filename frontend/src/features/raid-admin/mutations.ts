import { useMutation, useQueryClient } from '@tanstack/react-query';

import { bossQueryKeys } from '@/features/boss/queryKeys';

import { feedQueryKeys } from '@/features/feed';

import { libraryQueryKeys } from '@/features/library';

import {
  changeAdminBookCover,
  changeAdminBossBookCover,
  changeAdminBossRunPhaseImage,
  createAdminBoss,
  createAdminBossRun,
  runAdminBossRunAction,
  updateAdminBoss,
  updateAdminBookVisibility,
  updateAdminBossPhaseTemplates,
  updateAdminBossRun,
  updateAdminBossStatus,
} from './api';
import { raidAdminQueryKeys } from './queryKeys';
import type {
  AdminBossCreateRequest,
  AdminBossPhaseImageUpdateRequest,
  AdminBossPhaseTemplatesUpdateRequest,
  AdminBossRunActionRequest,
  AdminBossRunCreateRequest,
  AdminBossRunUpdateRequest,
  AdminBossStatusUpdateRequest,
  AdminBossUpdateRequest,
  AdminBookVisibilityUpdateRequest,
} from './types';

export function useCreateAdminBossMutation() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (request: AdminBossCreateRequest) => createAdminBoss(request),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: raidAdminQueryKeys.all }),
  });
}

export function useUpdateAdminBossMutation() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ bossId, request }: { bossId: number; request: AdminBossUpdateRequest }) =>
      updateAdminBoss(bossId, request),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: raidAdminQueryKeys.all }),
  });
}

/**
 * 페이즈 템플릿을 전체 교체한다. 템플릿은 다음에 만들 레이드의 기본값이므로 이미 진행 중인
 * 회차의 스냅샷은 바뀌지 않는다. 공개 조회를 무효화하지 않는 이유도 같다.
 */
export function useUpdateAdminBossPhaseTemplatesMutation() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({
      bossId,
      request,
    }: {
      bossId: number;
      request: AdminBossPhaseTemplatesUpdateRequest;
    }) => updateAdminBossPhaseTemplates(bossId, request),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: raidAdminQueryKeys.all }),
  });
}

/**
 * 보스를 활성·비활성으로 전환한다. 비활성 보스는 새 레이드 선택지에서 빠지고(bossOptions),
 * 공개 레이드 목록도 영향을 받으므로 둘 다 무효화한다.
 */
export function useUpdateAdminBossStatusMutation() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ bossId, request }: { bossId: number; request: AdminBossStatusUpdateRequest }) =>
      updateAdminBossStatus(bossId, request),
    onSuccess: () =>
      Promise.all([
        queryClient.invalidateQueries({ queryKey: raidAdminQueryKeys.all }),
        queryClient.invalidateQueries({ queryKey: bossQueryKeys.lists() }),
      ]),
  });
}

/**
 * 보스의 기본 표지를 교체한다. 책 표지는 생성 시점에 복사되는 스냅샷이므로 이 교체는
 * 앞으로 만들어지는 책에만 반영된다. 이미 만들어진 책은 책별 교체로 고쳐야 한다.
 *
 * 공개 보스 조회(bossQueryKeys)는 무효화하지 않는다. BossRunResponse와
 * BossRunSummaryResponse는 기본 표지를 담지 않고, currentPhase.imageUrl은 페이즈
 * 이미지라 이 교체와 무관하다.
 */
export function useChangeBossBookCoverMutation() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ bossId, imageAssetId }: { bossId: number; imageAssetId: number }) =>
      changeAdminBossBookCover(bossId, imageAssetId),
    // 재조회까지 기다린다. 호출부가 이 Promise로 진행 표시를 유지하므로, 버리면 바뀐
    // 표지가 그려지기 전에 완료로 보인다.
    onSuccess: () => queryClient.invalidateQueries({ queryKey: raidAdminQueryKeys.all }),
  });
}

export function useChangeBookCoverMutation() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ bookId, imageAssetId }: { bookId: number; imageAssetId: number }) =>
      changeAdminBookCover(bookId, imageAssetId),
    // 목록과 상세 모두 표지를 담고 있어 둘 다 무효화해야 바뀐 표지가 보인다.
    // 보스 표지 교체와 같은 컨트롤에서 쓰이므로 재조회를 기다리는 것도 맞춘다.
    onSuccess: (_data, { bookId }) =>
      Promise.all([
        queryClient.invalidateQueries({ queryKey: libraryQueryKeys.lists() }),
        queryClient.invalidateQueries({ queryKey: libraryQueryKeys.detail(bookId) }),
      ]),
  });
}

/**
 * 레이드 설정을 수정한다. 예정 회차는 전체 설정을, 진행 중 회차는 endsAt만 바꿀 수 있다.
 * 공개 레이드 목록·상세가 일정과 페이즈를 그대로 보여 주므로 함께 무효화한다.
 */
export function useUpdateAdminBossRunMutation() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ runId, request }: { runId: number; request: AdminBossRunUpdateRequest }) =>
      updateAdminBossRun(runId, request),
    onSuccess: (_data, { runId }) => invalidateRunSurfaces(queryClient, runId),
  });
}

/** 즉시 시작·취소·조기 종료. 상태가 공개 목록의 노출 여부를 바꾸므로 공개 쿼리까지 무효화한다. */
export function useAdminBossRunActionMutation() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({
      runId,
      action,
      request,
    }: {
      runId: number;
      action: 'activate' | 'cancel' | 'end';
      request: AdminBossRunActionRequest;
    }) => runAdminBossRunAction(runId, action, request),
    onSuccess: (_data, { runId }) => invalidateRunSurfaces(queryClient, runId),
  });
}

/**
 * 진행 중·예정 회차의 페이즈 이미지를 교체한다. 참여자 화면이 현재 페이즈 이미지를 그대로
 * 쓰므로 공개 조회를 함께 무효화해야 바뀐 이미지가 바로 보인다.
 */
export function useChangeAdminBossRunPhaseImageMutation() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({
      runId,
      phaseNo,
      request,
    }: {
      runId: number;
      phaseNo: number;
      request: AdminBossPhaseImageUpdateRequest;
    }) => changeAdminBossRunPhaseImage(runId, phaseNo, request),
    onSuccess: (_data, { runId }) => invalidateRunSurfaces(queryClient, runId),
  });
}

/**
 * 책 공개 상태를 전환한다. 숨긴 책은 도서관 목록·상세와 주간 피드에서 사라져야 하므로
 * 공개 쿼리를 함께 무효화한다.
 */
export function useUpdateAdminBookVisibilityMutation() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({
      bookId,
      request,
    }: {
      bookId: number;
      request: AdminBookVisibilityUpdateRequest;
    }) => updateAdminBookVisibility(bookId, request),
    onSuccess: () =>
      Promise.all([
        queryClient.invalidateQueries({ queryKey: raidAdminQueryKeys.all }),
        // libraryQueryKeys.all은 접두사 매칭으로 목록·상세·북마크를 모두 포함한다.
        queryClient.invalidateQueries({ queryKey: libraryQueryKeys.all }),
        queryClient.invalidateQueries({ queryKey: feedQueryKeys.all }),
      ]),
  });
}

function invalidateRunSurfaces(
  queryClient: ReturnType<typeof useQueryClient>,
  runId: number,
) {
  return Promise.all([
    queryClient.invalidateQueries({ queryKey: raidAdminQueryKeys.all }),
    queryClient.invalidateQueries({ queryKey: bossQueryKeys.lists() }),
    queryClient.invalidateQueries({ queryKey: bossQueryKeys.detail(runId) }),
    queryClient.invalidateQueries({ queryKey: bossQueryKeys.myRunLists() }),
  ]);
}

export function useCreateAdminBossRunMutation() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (request: AdminBossRunCreateRequest) => createAdminBossRun(request),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: raidAdminQueryKeys.all });
      void queryClient.invalidateQueries({ queryKey: bossQueryKeys.lists() });
    },
  });
}
