import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { act, renderHook } from '@testing-library/react';
import type { PropsWithChildren } from 'react';
import { describe, expect, it, vi } from 'vitest';

import { bossQueryKeys } from '@/features/boss/queryKeys';
import { feedQueryKeys } from '@/features/feed';
import { libraryQueryKeys } from '@/features/library';
import { seedAdminBossRun } from '@/mocks/handlers/raidAdmin.handlers';

import { raidAdminQueryKeys } from './queryKeys';
import {
  useAdminBossRunActionMutation,
  useChangeAdminBossRunPhaseImageMutation,
  useCreateAdminBossMutation,
  useUpdateAdminBookVisibilityMutation,
  useUpdateAdminBossMutation,
  useUpdateAdminBossPhaseTemplatesMutation,
  useUpdateAdminBossStatusMutation,
} from './mutations';

function setUp() {
  const queryClient = new QueryClient({ defaultOptions: { mutations: { retry: false } } });
  const invalidateQueries = vi.spyOn(queryClient, 'invalidateQueries');
  const wrapper = ({ children }: PropsWithChildren) => (
    <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
  );
  return { wrapper, invalidateQueries };
}

describe('레이드 관리자 mutation', () => {
  it('보스 생성 성공 후 관리자 목록 캐시를 갱신한다', async () => {
    const { wrapper, invalidateQueries } = setUp();
    const { result } = renderHook(() => useCreateAdminBossMutation(), { wrapper });

    await act(async () => {
      await result.current.mutateAsync({
        name: '기억의 파수꾼',
        description: '기억을 지키는 보스',
        bookCoverImageAssetId: 201,
        moods: [{ code: 'MYSTERY', role: 'THEME', weight: 1 }],
        phaseTemplates: [{ imageAssetId: 101 }],
      });
    });

    expect(invalidateQueries).toHaveBeenCalledWith({ queryKey: raidAdminQueryKeys.all });
  });

  it('보스 정보 수정 성공 후 관리자 목록 캐시를 갱신한다', async () => {
    const { wrapper, invalidateQueries } = setUp();
    const { result } = renderHook(() => useUpdateAdminBossMutation(), { wrapper });

    await act(async () => {
      await result.current.mutateAsync({
        bossId: 3,
        request: {
          name: '수정된 보스',
          description: '수정된 설명',
          moods: [{ code: 'CALM', role: 'THEME', weight: 1 }],
          expectedUpdatedAt: '2026-07-30T01:23:45Z',
        },
      });
    });

    expect(invalidateQueries).toHaveBeenCalledWith({ queryKey: raidAdminQueryKeys.all });
  });

  it('페이즈 템플릿 교체 성공 후 관리자 목록 캐시를 갱신한다', async () => {
    const { wrapper, invalidateQueries } = setUp();
    const { result } = renderHook(() => useUpdateAdminBossPhaseTemplatesMutation(), { wrapper });

    await act(async () => {
      await result.current.mutateAsync({
        bossId: 3,
        request: {
          phaseTemplates: [{ maxHp: 1800, imageAssetId: 101 }],
          expectedUpdatedAt: '2026-07-30T01:23:45Z',
        },
      });
    });

    expect(invalidateQueries).toHaveBeenCalledWith({ queryKey: raidAdminQueryKeys.all });
  });

  it('레이드 상태 전환 성공 후 공개 레이드 목록·상세까지 갱신한다', async () => {
    // 취소·조기 종료는 참여자 화면의 노출 여부와 진행 상태를 바꾼다.
    const { wrapper, invalidateQueries } = setUp();
    seedAdminBossRun({
      id: 7,
      bossId: 3,
      bossName: '새벽의 수호자',
      status: 'SCHEDULED',
      currentPhaseNo: 1,
      startedAt: null,
      endsAt: '2026-08-08T00:00:00Z',
      phases: [],
      createdAt: '2026-08-01T00:00:00Z',
      updatedAt: '2026-08-01T00:00:00Z',
    });
    const { result } = renderHook(() => useAdminBossRunActionMutation(), { wrapper });

    await act(async () => {
      await result.current.mutateAsync({
        runId: 7,
        action: 'cancel',
        request: { expectedUpdatedAt: '2026-08-01T00:00:00Z' },
      });
    });

    expect(invalidateQueries).toHaveBeenCalledWith({ queryKey: raidAdminQueryKeys.all });
    expect(invalidateQueries).toHaveBeenCalledWith({ queryKey: bossQueryKeys.lists() });
    expect(invalidateQueries).toHaveBeenCalledWith({ queryKey: bossQueryKeys.detail(7) });
    expect(invalidateQueries).toHaveBeenCalledWith({ queryKey: bossQueryKeys.myRunLists() });
  });

  it('페이즈 이미지 교체 성공 후 공개 보스런 조회를 갱신한다', async () => {
    // 참여자 화면이 현재 페이즈 이미지를 그대로 쓰므로 교체가 바로 보여야 한다.
    const { wrapper, invalidateQueries } = setUp();
    seedAdminBossRun({
      id: 8,
      bossId: 3,
      bossName: '새벽의 수호자',
      status: 'ACTIVE',
      currentPhaseNo: 1,
      startedAt: '2026-08-01T00:00:00Z',
      endsAt: '2026-08-08T00:00:00Z',
      phases: [
        {
          phaseNo: 1,
          maxHp: 1000,
          remainingHp: 800,
          hardMinScore: null,
          recommendedScore: null,
          damageMultiplier: 1,
          imageAssetId: 101,
          imageUrl: '/images/mock.webp',
          status: 'ACTIVE',
        },
      ],
      createdAt: '2026-08-01T00:00:00Z',
      updatedAt: '2026-08-01T00:00:00Z',
    });
    const { result } = renderHook(() => useChangeAdminBossRunPhaseImageMutation(), { wrapper });

    await act(async () => {
      await result.current.mutateAsync({
        runId: 8,
        phaseNo: 1,
        request: { imageAssetId: 321, expectedUpdatedAt: '2026-08-01T00:00:00Z' },
      });
    });

    expect(invalidateQueries).toHaveBeenCalledWith({ queryKey: bossQueryKeys.detail(8) });
  });

  it('책 공개 상태 전환 성공 후 도서관과 피드까지 갱신한다', async () => {
    // 숨긴 책은 도서관 목록·상세와 주간 문장 피드에서 함께 사라져야 한다.
    const { wrapper, invalidateQueries } = setUp();
    const { result } = renderHook(() => useUpdateAdminBookVisibilityMutation(), { wrapper });

    await act(async () => {
      await result.current.mutateAsync({
        bookId: 18,
        request: { visibility: 'HIDDEN', expectedUpdatedAt: '2026-07-30T10:05:00Z' },
      });
    });

    expect(invalidateQueries).toHaveBeenCalledWith({ queryKey: raidAdminQueryKeys.all });
    // libraryQueryKeys.all은 접두사 매칭으로 목록·상세를 함께 비운다.
    expect(invalidateQueries).toHaveBeenCalledWith({ queryKey: libraryQueryKeys.all });
    expect(invalidateQueries).toHaveBeenCalledWith({ queryKey: feedQueryKeys.all });
  });

  it('상태 전환 성공 후 공개 레이드 목록까지 갱신한다', async () => {
    // 비활성 보스는 새 레이드 선택지에서 빠져야 하고, 공개 목록도 영향을 받는다.
    const { wrapper, invalidateQueries } = setUp();
    const { result } = renderHook(() => useUpdateAdminBossStatusMutation(), { wrapper });

    await act(async () => {
      await result.current.mutateAsync({
        bossId: 3,
        request: { status: 'INACTIVE', expectedUpdatedAt: '2026-07-30T01:23:45Z' },
      });
    });

    expect(invalidateQueries).toHaveBeenCalledWith({ queryKey: raidAdminQueryKeys.all });
    expect(invalidateQueries).toHaveBeenCalledWith({ queryKey: bossQueryKeys.lists() });
  });
});
