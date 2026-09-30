import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { HttpResponse, http } from 'msw';
import { MemoryRouter } from 'react-router-dom';
import { describe, expect, it } from 'vitest';

import type { BossRunSummaryResponse } from '@/features/boss';
import { AuthGateProvider } from '@/features/auth';
import { server } from '@/mocks/server';

import { BossListPage } from './BossListPage';

function createBossRun(overrides: Partial<BossRunSummaryResponse>): BossRunSummaryResponse {
  return {
    bossRunId: 1,
    bossId: 1,
    bossName: '테스트 레이드',
    status: 'ACTIVE',
    startedAt: '2026-08-05T00:00:00Z',
    endsAt: '2026-08-06T00:00:00Z',
    clearedAt: null,
    accumulatedDamage: 0,
    currentPhase: {
      phaseId: 1,
      phaseNo: 1,
      maxHp: 1000,
      remainingHp: 1000,
      progressPercent: 0,
      hardMinScore: 40,
      recommendedScore: 70,
      damageMultiplier: 1,
      imageUrl: null,
      status: 'ACTIVE',
    },
    moods: [],
    mySubmissionCount: 0,
    remainingSubmissionCount: 3,
    ...overrides,
  };
}

function renderBossListPage() {
  const queryClient = new QueryClient({
    defaultOptions: {
      queries: {
        retry: false,
      },
    },
  });

  render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter>
        <AuthGateProvider>
          <BossListPage />
        </AuthGateProvider>
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

function loginAsMember() {
  server.use(
    http.get('*/api/v1/users/me', () =>
      HttpResponse.json({
        id: 1,
        email: 'writer@example.com',
        nickname: '문장여행자',
        status: 'ACTIVE',
        role: 'USER',
        createdAt: '2026-07-30T00:00:00Z',
      }),
    ),
  );
}

describe('BossListPage', () => {
  it('비로그인 사용자가 보스 카드를 누르면 로그인 안내를 표시한다', async () => {
    const user = userEvent.setup();
    renderBossListPage();

    const [bossCard] = await screen.findAllByRole('link', { name: /상세 보기/ });
    await user.click(bossCard);

    expect(await screen.findByRole('dialog')).toHaveTextContent('로그인이 필요한 기능이에요');
    expect(screen.getByRole('link', { name: '로그인하러 가기' })).toHaveAttribute(
      'href',
      '/login',
    );
  });

  it('카드에는 회차 상태 뱃지를 표시하지 않는다', async () => {
    renderBossListPage();

    // 탭 버튼에도 같은 문구가 있으므로 카드 안쪽만 확인한다.
    const card = await screen.findByRole('link', { name: '새벽의 수호자 상세 보기' });
    expect(card).not.toHaveTextContent('진행 중');
  });

  it('종료 탭에서만 결과 하위 필터가 나타나고 선택한 결과만 조회한다', async () => {
    const user = userEvent.setup();
    const requestedStatuses: string[][] = [];

    server.use(
      http.get('*/api/v1/boss-runs', ({ request }) => {
        const statuses = new URL(request.url).searchParams.getAll('status');
        requestedStatuses.push(statuses);

        const runs = [
          createBossRun({ bossRunId: 13, bossName: '클리어 레이드', status: 'CLEARED' }),
          createBossRun({ bossRunId: 14, bossName: '미클리어 레이드', status: 'ENDED_UNCLEARED' }),
          createBossRun({ bossRunId: 15, bossName: '마감 레이드', status: 'CLOSED' }),
        ].filter((run) => statuses.includes(run.status));

        return HttpResponse.json({
          content: runs,
          page: 0,
          size: 10,
          totalElements: runs.length,
          totalPages: 1,
          hasNext: false,
        });
      }),
    );

    renderBossListPage();

    // 기본 탭(진행 중)에는 해당 회차가 없어 빈 목록 안내로 로딩 완료를 확인한다.
    await screen.findByText('표시할 원정이 없습니다');
    expect(screen.queryByRole('group', { name: '종료 결과' })).not.toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: '종료' }));

    // 종료 탭 기본값은 클리어다.
    expect(await screen.findByText('클리어 레이드')).toBeInTheDocument();
    expect(screen.queryByText('마감 레이드')).not.toBeInTheDocument();

    const endedGroup = screen.getByRole('group', { name: '종료 결과' });
    await user.click(within(endedGroup).getByRole('button', { name: '마감' }));

    // 마감은 미클리어 종료까지 함께 담아 어떤 종료 회차도 목록에서 빠지지 않는다.
    expect(await screen.findByText('마감 레이드')).toBeInTheDocument();
    expect(screen.getByText('미클리어 레이드')).toBeInTheDocument();
    expect(screen.queryByText('클리어 레이드')).not.toBeInTheDocument();

    expect(requestedStatuses).toEqual([
      ['ACTIVE'],
      ['CLEARED'],
      ['ENDED_UNCLEARED', 'CLOSED'],
    ]);
  });

  it('제출 이력이 있는 회차에만 참여중 뱃지를 표시한다', async () => {
    loginAsMember();
    server.use(
      http.get('*/api/v1/boss-runs', () =>
        HttpResponse.json({
          content: [
            createBossRun({ bossRunId: 21, bossName: '참여한 원정' }),
            createBossRun({ bossRunId: 22, bossName: '참여 안 한 레이드' }),
          ],
          page: 0,
          size: 10,
          totalElements: 2,
          totalPages: 1,
          hasNext: false,
        }),
      ),
      // 참여 판정은 오늘 제출 수가 아니라 제출 이력이 있는 회차 목록으로 한다.
      http.get('*/api/v1/me/boss-runs', () =>
        HttpResponse.json({
          content: [
            {
              bossRunId: 21,
              bossId: 1,
              bossName: '참여한 원정',
              status: 'ACTIVE',
              currentPhaseNo: 1,
              startedAt: '2026-08-01T00:00:00Z',
              endsAt: '2026-08-09T00:00:00Z',
              clearedAt: null,
              lastSubmittedAt: '2026-08-02T00:00:00Z',
              bookId: null,
              bookStatus: null,
            },
          ],
          page: 0,
          size: 100,
          totalElements: 1,
          totalPages: 1,
          hasNext: false,
        }),
      ),
    );

    renderBossListPage();

    const joinedCard = await screen.findByRole('link', { name: '참여한 원정 상세 보기' });
    await waitFor(() => expect(joinedCard).toHaveTextContent('참여중'));

    expect(
      screen.getByRole('link', { name: '참여 안 한 레이드 상세 보기' }),
    ).not.toHaveTextContent('참여중');
  });

  it('비회원에게는 참여중 뱃지를 표시하지 않는다', async () => {
    renderBossListPage();

    const card = await screen.findByRole('link', { name: '새벽의 수호자 상세 보기' });
    expect(card).not.toHaveTextContent('참여중');
  });

  it('진행 중 레이드 목록을 기본으로 표시한다', async () => {
    renderBossListPage();

    expect(await screen.findByText('새벽의 수호자')).toBeInTheDocument();

    // 기본 필터가 진행 중이므로 예정(SCHEDULED) 회차는 보이지 않는다.
    expect(screen.queryByText('기억의 파수꾼')).not.toBeInTheDocument();

    expect(screen.getByRole('link', { name: '새벽의 수호자 상세 보기' })).toHaveAttribute(
      'href',
      '/boss/7',
    );
    expect(screen.getByText('HP 62%')).toBeInTheDocument();
    expect(screen.getByRole('progressbar', { name: '새벽의 수호자 남은 HP' })).toHaveAttribute(
      'aria-valuenow',
      '62',
    );
  });

  it('탭을 누르면 그 탭이 묶는 상태만 서버에 요청한다', async () => {
    const user = userEvent.setup();
    const requestedStatuses: string[][] = [];

    server.use(
      http.get('*/api/v1/boss-runs', ({ request }) => {
        const statuses = new URL(request.url).searchParams.getAll('status');
        requestedStatuses.push(statuses);

        const runs = [
          createBossRun({ bossRunId: 11, bossName: '예정 레이드', status: 'SCHEDULED' }),
          createBossRun({ bossRunId: 12, bossName: '진행 레이드', status: 'ACTIVE' }),
          createBossRun({ bossRunId: 13, bossName: '클리어 레이드', status: 'CLEARED' }),
          createBossRun({ bossRunId: 14, bossName: '미클리어 레이드', status: 'ENDED_UNCLEARED' }),
          createBossRun({ bossRunId: 15, bossName: '마감 레이드', status: 'CLOSED' }),
        ].filter((run) => statuses.includes(run.status));

        return HttpResponse.json({
          content: runs,
          page: 0,
          size: 10,
          totalElements: runs.length,
          totalPages: 1,
          hasNext: false,
        });
      }),
    );

    renderBossListPage();

    expect(await screen.findByText('진행 레이드')).toBeInTheDocument();
    expect(requestedStatuses).toEqual([['ACTIVE']]);

    await user.click(screen.getByRole('button', { name: '예정' }));

    expect(await screen.findByText('예정 레이드')).toBeInTheDocument();
    expect(screen.queryByText('진행 레이드')).not.toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: '종료' }));

    // 종료 탭은 결과 필터로 나뉘고 기본값은 클리어다. 여러 상태를 한 필터가 덮을 때는
    // 요청이 여러 번 나가면 페이지네이션을 클라이언트가 다시 맞춰야 하므로,
    // 한 번의 요청에 상태가 모두 실려야 한다.
    expect(await screen.findByText('클리어 레이드')).toBeInTheDocument();

    await user.click(
      within(screen.getByRole('group', { name: '종료 결과' })).getByRole('button', {
        name: '마감',
      }),
    );

    expect(await screen.findByText('마감 레이드')).toBeInTheDocument();
    expect(screen.getByText('미클리어 레이드')).toBeInTheDocument();
    expect(requestedStatuses).toEqual([
      ['ACTIVE'],
      ['SCHEDULED'],
      ['CLEARED'],
      ['ENDED_UNCLEARED', 'CLOSED'],
    ]);
  });

  it('조회 결과가 없으면 빈 목록 안내를 표시한다', async () => {
    const user = userEvent.setup();

    renderBossListPage();

    await screen.findByText('새벽의 수호자');

    await user.click(
      screen.getByRole('button', {
        name: '종료',
      }),
    );

    expect(await screen.findByText('표시할 원정이 없습니다')).toBeInTheDocument();
  });

  it('목록 조회에 실패하면 오류와 재시도 버튼을 표시한다', async () => {
    server.use(
      http.get('*/api/v1/boss-runs', () => {
        return HttpResponse.json(
          {
            occurredAt: new Date().toISOString(),
            code: 'INTERNAL_ERROR',
            message: '보스전 목록 조회에 실패했습니다.',
            fieldErrors: [],
          },
          {
            status: 500,
          },
        );
      }),
    );

    renderBossListPage();

    expect(await screen.findByRole('alert')).toHaveTextContent('원정을 불러오지 못했습니다');

    expect(
      screen.getByRole('button', {
        name: '다시 시도',
      }),
    ).toBeInTheDocument();
  });

  it('다음 페이지는 서버에 그 페이지를 요청해서 받는다', async () => {
    const user = userEvent.setup();
    const requestedPages: string[] = [];

    server.use(
      http.get('*/api/v1/boss-runs', ({ request }) => {
        const url = new URL(request.url);
        const page = Number(url.searchParams.get('page') ?? '0');
        const size = Number(url.searchParams.get('size') ?? '10');
        requestedPages.push(String(page));

        const all = Array.from({ length: 11 }, (_, index) =>
          createBossRun({
            bossRunId: 100 + index,
            bossName: `진행 레이드 ${index + 1}`,
            status: 'ACTIVE',
          }),
        );
        const start = page * size;

        return HttpResponse.json({
          content: all.slice(start, start + size),
          page,
          size,
          totalElements: all.length,
          totalPages: Math.ceil(all.length / size),
          hasNext: start + size < all.length,
        });
      }),
    );

    renderBossListPage();

    expect(await screen.findByText('1 / 2')).toBeInTheDocument();
    expect(requestedPages).toEqual(['0']);

    const nextButton = screen.getByRole('button', { name: '다음' });
    expect(nextButton).toBeEnabled();

    await user.click(nextButton);

    expect(await screen.findByText('2 / 2')).toBeInTheDocument();
    expect(screen.getByText('진행 레이드 11')).toBeInTheDocument();
    expect(screen.queryByText('진행 레이드 1')).not.toBeInTheDocument();
    // 전체를 받아 두고 자르는 것이 아니라 그 페이지만 받는다.
    expect(requestedPages).toEqual(['0', '1']);

    expect(screen.getByRole('button', { name: '이전' })).toBeEnabled();
    expect(screen.getByRole('button', { name: '다음' })).toBeDisabled();
  });
});
