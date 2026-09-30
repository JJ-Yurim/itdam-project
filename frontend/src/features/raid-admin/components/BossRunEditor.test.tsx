import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { HttpResponse, http } from 'msw';
import { describe, expect, it } from 'vitest';

import { ToastProvider } from '@/shared/components';
import { seedAdminBossRun } from '@/mocks/handlers/raidAdmin.handlers';
import { server } from '@/mocks/server';

import type { AdminBossRun, BossRunStatus } from '../types';
import { BossRunEditor } from './BossRunEditor';

function run(status: BossRunStatus): AdminBossRun {
  return {
    id: 7,
    bossId: 3,
    bossName: '새벽의 수호자',
    status,
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
        status: status === 'SCHEDULED' ? 'WAITING' : 'ACTIVE',
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
  };
}

function renderEditor(target: AdminBossRun) {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  });

  render(
    <QueryClientProvider client={queryClient}>
      <ToastProvider>
        <BossRunEditor run={target} onClose={() => {}} />
      </ToastProvider>
    </QueryClientProvider>,
  );
}

function imageFile(name: string) {
  return new File(['fake-image-bytes'], name, { type: 'image/webp' });
}

describe('BossRunEditor', () => {
  it('진행 중 레이드는 종료 시각만 수정할 수 있다', async () => {
    const user = userEvent.setup();
    let patchBody: Record<string, unknown> | undefined;
    server.use(
      http.patch('*/api/admin/boss-runs/:runId', async ({ request }) => {
        patchBody = (await request.json()) as Record<string, unknown>;
        return HttpResponse.json(run('ACTIVE'));
      }),
    );

    renderEditor(run('ACTIVE'));

    // 보스와 페이즈 설정은 진행 중에는 서버가 거절하므로 화면에도 두지 않는다.
    expect(screen.queryByLabelText(/보스/)).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: '페이즈 추가' })).not.toBeInTheDocument();

    const endsAt = screen.getByLabelText(/종료 시각/);
    await user.clear(endsAt);
    await user.type(endsAt, '2026-08-09T12:00');
    await user.click(screen.getByRole('button', { name: '일정 저장' }));

    await waitFor(() => expect(patchBody).toBeDefined());
    expect(patchBody?.expectedUpdatedAt).toBe('2026-08-01T00:00:00Z');
    expect(String(patchBody?.endsAt)).toContain('2026-08-09');
    expect(patchBody).not.toHaveProperty('phases');
  });

  it('진행 중 레이드의 모든 페이즈 이미지를 개별 교체한다', async () => {
    const user = userEvent.setup();
    const calls: { pathname: string; body: unknown }[] = [];
    server.use(
      http.post('*/api/admin/boss-images', () =>
        HttpResponse.json({ imageAssetId: 777, imageUrl: '/images/new.webp' }, { status: 201 }),
      ),
      http.put('*/api/admin/boss-runs/:runId/phases/:phaseNo/image', async ({ request }) => {
        calls.push({ pathname: new URL(request.url).pathname, body: await request.json() });
        return HttpResponse.json({ ...run('ACTIVE'), updatedAt: '2026-08-02T00:00:00Z' });
      }),
    );

    renderEditor(run('ACTIVE'));

    // 완료·진행·대기 페이즈 모두 교체 대상이다.
    await user.upload(screen.getByLabelText('2번째 페이즈 새 이미지'), imageFile('phase.webp'));
    await user.click(screen.getByRole('button', { name: '2번째 페이즈 이미지 변경' }));

    await waitFor(() => expect(calls).toHaveLength(1));
    expect(calls[0].pathname).toBe('/api/admin/boss-runs/7/phases/2/image');
    expect(calls[0].body).toEqual({ imageAssetId: 777, expectedUpdatedAt: '2026-08-01T00:00:00Z' });
  });

  it('교체 응답으로 패널의 이미지와 다음 요청의 expectedUpdatedAt을 갱신한다', async () => {
    // 목록 스냅샷을 그대로 두면 교체 후에도 옛 이미지가 남고, 이어지는 저장이 409가 된다.
    const user = userEvent.setup();
    const sentExpectations: unknown[] = [];
    server.use(
      http.post('*/api/admin/boss-images', () =>
        HttpResponse.json({ imageAssetId: 777, imageUrl: '/images/new.webp' }, { status: 201 }),
      ),
      http.put('*/api/admin/boss-runs/:runId/phases/:phaseNo/image', async ({ request }) => {
        const body = (await request.json()) as { expectedUpdatedAt: string };
        sentExpectations.push(body.expectedUpdatedAt);
        const updated = run('ACTIVE');
        return HttpResponse.json({
          ...updated,
          updatedAt: '2026-08-02T00:00:00Z',
          phases: updated.phases.map((phase) =>
            phase.phaseNo === 1 ? { ...phase, imageUrl: '/images/replaced-777.webp' } : phase,
          ),
        });
      }),
      http.patch('*/api/admin/boss-runs/:runId', async ({ request }) => {
        const body = (await request.json()) as { expectedUpdatedAt: string };
        sentExpectations.push(body.expectedUpdatedAt);
        return HttpResponse.json({ ...run('ACTIVE'), updatedAt: '2026-08-03T00:00:00Z' });
      }),
    );

    renderEditor(run('ACTIVE'));
    await user.upload(screen.getByLabelText('1번째 페이즈 새 이미지'), imageFile('phase.webp'));
    await user.click(screen.getByRole('button', { name: '1번째 페이즈 이미지 변경' }));

    await waitFor(() =>
      expect(screen.getByAltText('1번째 페이즈 현재 이미지')).toHaveAttribute(
        'src',
        '/images/replaced-777.webp',
      ),
    );

    await user.click(screen.getByRole('button', { name: '일정 저장' }));
    await waitFor(() => expect(sentExpectations).toHaveLength(2));
    expect(sentExpectations).toEqual(['2026-08-01T00:00:00Z', '2026-08-02T00:00:00Z']);
  });

  it('예정 레이드는 전체 교체 계약에 맞춰 보스와 현재 페이즈를 함께 보낸다', async () => {
    // 서버는 예정 회차 수정을 전체 교체로 다룬다. bossId나 phases가 없으면 400이다.
    const user = userEvent.setup();
    let patchBody: Record<string, unknown> | undefined;
    server.use(
      http.patch('*/api/admin/boss-runs/:runId', async ({ request }) => {
        patchBody = (await request.json()) as Record<string, unknown>;
        return HttpResponse.json(run('SCHEDULED'));
      }),
    );

    renderEditor(run('SCHEDULED'));
    await user.click(screen.getByRole('button', { name: '일정 저장' }));

    await waitFor(() => expect(patchBody).toBeDefined());
    expect(patchBody?.bossId).toBe(3);
    expect(patchBody?.phases).toEqual([
      { maxHp: 1000, damageMultiplier: 1, imageAssetId: 101 },
      { maxHp: 2000, damageMultiplier: 1, imageAssetId: 102 },
    ]);
  });

  it('예정 회차 수정이 목 계약에서도 400 없이 통과한다', async () => {
    // 목이 서버처럼 bossId·phases를 요구하므로, 빠지면 이 테스트가 400을 잡아낸다.
    const user = userEvent.setup();
    seedAdminBossRun({ ...run('SCHEDULED'), id: 77 });
    renderEditor({ ...run('SCHEDULED'), id: 77 });

    await user.click(screen.getByRole('button', { name: '일정 저장' }));

    expect(await screen.findByText('레이드 일정을 수정했습니다.')).toBeInTheDocument();
  });

  it('페이즈 이미지 교체가 409면 복구 배너를 띄운다', async () => {
    // 자체 mutation을 쓰므로 부모가 충돌을 모르면 배너 없이 계속 실패만 반복된다.
    const user = userEvent.setup();
    server.use(
      http.post('*/api/admin/boss-images', () =>
        HttpResponse.json({ imageAssetId: 777, imageUrl: '/images/new.webp' }, { status: 201 }),
      ),
      http.put('*/api/admin/boss-runs/:runId/phases/:phaseNo/image', () =>
        HttpResponse.json({ code: 'CONFLICT' }, { status: 409 }),
      ),
    );

    renderEditor(run('ACTIVE'));
    await user.upload(screen.getByLabelText('1번째 페이즈 새 이미지'), imageFile('phase.webp'));
    await user.click(screen.getByRole('button', { name: '1번째 페이즈 이미지 변경' }));

    // 토스트도 alert이라 배너는 문구로 찾는다.
    expect(
      await screen.findByText(/다른 관리자가 먼저 이 레이드를 수정했습니다/),
    ).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '최신 데이터 다시 불러오기' })).toBeInTheDocument();
  });

  it('종료된 레이드는 읽기 전용으로 보여 준다', () => {
    renderEditor(run('CLOSED'));

    expect(screen.getByText(/종료된 레이드는 수정할 수 없습니다/)).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: '일정 저장' })).not.toBeInTheDocument();
    expect(screen.queryByLabelText('1번째 페이즈 새 이미지')).not.toBeInTheDocument();
    // 상태 확인용 정보는 남는다.
    expect(screen.getByText(/800/)).toBeInTheDocument();
  });

  it('다른 관리자가 먼저 수정했으면 최신 데이터를 다시 불러오도록 안내한다', async () => {
    const user = userEvent.setup();
    server.use(
      http.patch('*/api/admin/boss-runs/:runId', () =>
        HttpResponse.json({ code: 'CONFLICT' }, { status: 409 }),
      ),
    );

    renderEditor(run('ACTIVE'));
    await user.click(screen.getByRole('button', { name: '일정 저장' }));

    // 토스트도 alert이라 배너는 문구로 찾는다.
    expect(
      await screen.findByText(/다른 관리자가 먼저 이 레이드를 수정했습니다/),
    ).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '최신 데이터 다시 불러오기' })).toBeInTheDocument();
  });
});
