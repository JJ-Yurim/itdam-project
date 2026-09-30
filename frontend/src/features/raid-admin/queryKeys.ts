import type { AdminBookFilters, AdminBossFilters, AdminBossRunFilters } from './types';

export const raidAdminQueryKeys = {
  all: ['raid-admin'] as const,
  bosses: (filters: AdminBossFilters) =>
    [
      ...raidAdminQueryKeys.all,
      'bosses',
      filters.query ?? '',
      filters.status ?? '',
      filters.page ?? 0,
      filters.size ?? 20,
    ] as const,
  bossOptions: () => [...raidAdminQueryKeys.all, 'boss-options'] as const,
  bossRuns: (filters: AdminBossRunFilters) =>
    [
      ...raidAdminQueryKeys.all,
      'boss-runs',
      filters.query ?? '',
      (filters.statuses ?? []).join(','),
      filters.from ?? '',
      filters.to ?? '',
      filters.page ?? 0,
      filters.size ?? 20,
    ] as const,
  books: (filters: AdminBookFilters) =>
    [
      ...raidAdminQueryKeys.all,
      'books',
      filters.query ?? '',
      (filters.statuses ?? []).join(','),
      filters.visibility ?? '',
      filters.page ?? 0,
      filters.size ?? 20,
    ] as const,
};
