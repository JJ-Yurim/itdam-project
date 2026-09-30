import { keepPreviousData, useQuery } from '@tanstack/react-query';

import { getAdminBooks, getAdminBosses, getAdminBossRuns, getAllActiveAdminBosses } from './api';
import { raidAdminQueryKeys } from './queryKeys';
import type { AdminBookFilters, AdminBossFilters, AdminBossRunFilters } from './types';

// 페이지나 조건을 바꾸는 동안 이전 목록을 유지한다. 없으면 새 키가 pending이 되어 목록과
// 검색 입력이 함께 사라지고, 다음에 누를 대상이 화면에서 없어진다.
export function useAdminBossesQuery(filters: AdminBossFilters, enabled = true) {
  return useQuery({
    queryKey: raidAdminQueryKeys.bosses(filters),
    queryFn: () => getAdminBosses(filters),
    enabled,
    placeholderData: keepPreviousData,
  });
}

export function useAdminBossOptionsQuery(enabled = true) {
  return useQuery({
    queryKey: raidAdminQueryKeys.bossOptions(),
    queryFn: getAllActiveAdminBosses,
    enabled,
  });
}

export function useAdminBossRunsQuery(filters: AdminBossRunFilters, enabled = true) {
  return useQuery({
    queryKey: raidAdminQueryKeys.bossRuns(filters),
    queryFn: () => getAdminBossRuns(filters),
    enabled,
    placeholderData: keepPreviousData,
  });
}

export function useAdminBooksQuery(filters: AdminBookFilters, enabled = true) {
  return useQuery({
    queryKey: raidAdminQueryKeys.books(filters),
    queryFn: () => getAdminBooks(filters),
    enabled,
    placeholderData: keepPreviousData,
  });
}
