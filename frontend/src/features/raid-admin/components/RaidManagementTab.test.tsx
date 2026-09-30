import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { HttpResponse, http } from 'msw';
import { MemoryRouter } from 'react-router-dom';
import { beforeEach, describe, expect, it } from 'vitest';

import { ToastProvider } from '@/shared/components';
import { seedAdminBossRun } from '@/mocks/handlers/raidAdmin.handlers';
import { server } from '@/mocks/server';

import type { AdminBossRun, BossRunStatus } from '../types';
import { RaidManagementTab } from './RaidManagementTab';

function run(overrides: Partial<AdminBossRun> & { id: number; status: BossRunStatus }): AdminBossRun {
  return {
    bossId: 3,
    bossName: '새벽의 수호자',
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
      {
        phaseNo: 2,
        maxHp: 2000,
        remainingHp: 2000,
        hardMinScore: null,
        recommendedScore: null,
        damageMultiplier: 1,
        imageAssetId: 102,
        imageUrl: '/images/mock2.webp',
        status: 'WAITING',
      },
    ],
    createdAt: '2026-08-01T00:00:00Z',
    updatedAt: '2026-08-01T00:00:00Z',
    ...overrides,
  };
}

function renderTab(initialEntry = '/admin/raids?tab=runs') {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  });

  render(
    <QueryClientProvider client={queryClient}>
      <ToastProvider>
        <MemoryRouter initialEntries={[initialEntry]}>
          <RaidManagementTab />
        </MemoryRouter>
      </ToastProvider>
    </QueryClientProvider>,
  );
}

describe('RaidManagementTab', () => {
  beforeEach(() => {
    seedAdminBossRun(run({ id: 7, status: 'ACTIVE' }));
  });

  it('표에 보스·상태·일정·현재 페이즈·남은 HP를 표시한다', async () => {
    renderTab();
    const table = await screen.findByRole('table', { name: '레이드 목록' });
    const row = within(table).getByRole('row', { name: /새벽의 수호자/ });

    expect(within(row).getByText('진행 중')).toBeInTheDocument();
    expect(row).toHaveTextContent('1 / 2');
    // 남은 HP는 전체 페이즈 합계(800 + 2000)다. 부분 문자열로 두면 집계 방식이 바뀌어도 통과한다.
    expect(row).toHaveTextContent('2,800');
    expect(row).toHaveTextContent(/26\. 8\./);
  });

  it('진행 중 레이드에는 조기 종료와 페이즈 이미지 변경만 노출한다', async () => {
    renderTab();
    const table = await screen.findByRole('table', { name: '레이드 목록' });

    expect(within(table).getByRole('button', { name: /조기 종료/ })).toBeInTheDocument();
    expect(within(table).getByRole('button', { name: /수정/ })).toBeInTheDocument();
    expect(within(table).queryByRole('button', { name: /시작/ })).not.toBeInTheDocument();
    expect(within(table).queryByRole('button', { name: /취소/ })).not.toBeInTheDocument();
  });

  it('예정 레이드에는 시작과 취소를 노출한다', async () => {
    seedAdminBossRun(run({ id: 9, status: 'SCHEDULED', startedAt: null }));
    renderTab();
    const table = await screen.findByRole('table', { name: '레이드 목록' });
    const row = within(table).getAllByRole('row').find((candidate) => candidate.textContent?.includes('예정'))!;

    expect(within(row).getByRole('button', { name: /시작/ })).toBeInTheDocument();
    expect(within(row).getByRole('button', { name: /취소/ })).toBeInTheDocument();
    expect(within(row).queryByRole('button', { name: /조기 종료/ })).not.toBeInTheDocument();
  });

  it('종료된 레이드는 상태 전환 없이 상세만 열 수 있다', async () => {
    seedAdminBossRun(run({ id: 10, status: 'CLOSED' }));
    renderTab('/admin/raids?tab=runs&status=CLOSED');
    const table = await screen.findByRole('table', { name: '레이드 목록' });
    const row = within(table).getAllByRole('row').find((candidate) => candidate.textContent?.includes('마감'))!;

    expect(within(row).getByRole('button', { name: /상세/ })).toBeInTheDocument();
    expect(within(row).queryByRole('button', { name: /조기 종료/ })).not.toBeInTheDocument();
    expect(within(row).queryByRole('button', { name: /수정/ })).not.toBeInTheDocument();
  });

  it('조기 종료는 대상과 결과를 알려 주는 확인을 거친 뒤 반영된다', async () => {
    const user = userEvent.setup();
    renderTab();
    const table = await screen.findByRole('table', { name: '레이드 목록' });

    await user.click(within(table).getByRole('button', { name: /조기 종료/ }));

    const dialog = screen.getByRole('dialog', { name: '레이드 조기 종료' });
    expect(within(dialog).getByText(/새벽의 수호자/)).toBeInTheDocument();
    expect(within(dialog).getByText(/더 이상 문장을 받지 않습니다/)).toBeInTheDocument();

    await user.click(within(dialog).getByRole('button', { name: '조기 종료' }));

    await waitFor(() =>
      expect(
        within(screen.getByRole('table', { name: '레이드 목록' })).getByText('미클리어 종료'),
      ).toBeInTheDocument(),
    );
  });

  it('상태 필터를 서버 조회에 실어 보낸다', async () => {
    const user = userEvent.setup();
    const searches: string[] = [];
    server.use(
      http.get('*/api/admin/boss-runs', ({ request }) => {
        searches.push(new URL(request.url).search);
        return HttpResponse.json({
          content: [],
          page: 0,
          size: 20,
          totalElements: 0,
          totalPages: 0,
          hasNext: false,
        });
      }),
    );

    renderTab();
    await screen.findByText('생성된 레이드가 없습니다');

    await user.selectOptions(screen.getByRole('combobox', { name: '상태' }), 'SCHEDULED');
    await waitFor(() => expect(searches.at(-1)).toContain('status=SCHEDULED'));
  });

  it('목록을 불러오지 못하면 다시 시도를 제공한다', async () => {
    server.use(
      http.get('*/api/admin/boss-runs', () =>
        HttpResponse.json({ code: 'INTERNAL_ERROR' }, { status: 500 }),
      ),
    );
    renderTab();

    expect(await screen.findByRole('alert')).toHaveTextContent('레이드 목록을 불러오지 못했습니다');
    expect(screen.getByRole('button', { name: '다시 시도' })).toBeInTheDocument();
  });
});
