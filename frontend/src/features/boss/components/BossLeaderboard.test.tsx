import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { act, render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { HttpResponse, http } from 'msw';
import { describe, expect, it } from 'vitest';

import { server } from '@/mocks/server';

import { bossQueryKeys } from '../queryKeys';

import { BossLeaderboard } from './BossLeaderboard';

import type { BossRunLeaderboardResponse } from '../types';

const BOSS_RUN_ID = 7;

function renderLeaderboard(
  options: {
    myUserId?: number | null;
    isMyUserPending?: boolean;
    realtime?: boolean;
    variant?: 'full' | 'compact';
  } = {},
) {
  const { myUserId = null, isMyUserPending = false, realtime = true, variant = 'full' } = options;
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });

  render(
    <QueryClientProvider client={queryClient}>
      <BossLeaderboard
        bossRunId={BOSS_RUN_ID}
        myUserId={myUserId}
        isMyUserPending={isMyUserPending}
        realtime={realtime}
        variant={variant}
      />
    </QueryClientProvider>,
  );

  return { queryClient };
}

describe('BossLeaderboard', () => {
  it('compact 화면은 실시간 상위 순위와 목록 밖 내 순위를 함께 보여준다', async () => {
    const user = userEvent.setup();
    renderLeaderboard({ myUserId: 99, variant: 'compact' });

    const list = await screen.findByRole('list', { name: '실시간 기여도 미리보기' });
    const heading = screen.getByRole('heading', { name: '실시간 기여도' });
    const liveIndicator = screen.getByLabelText('실시간 순위 갱신 중');

    expect(list).toHaveTextContent('문장수집가');
    expect(heading).toHaveClass('lg:text-xl');
    expect(liveIndicator).toHaveClass('lg:text-sm');
    expect(list).toHaveClass('lg:space-y-1');
    expect(screen.getAllByRole('listitem')[0]).toHaveClass('lg:min-h-10', 'lg:text-base');
    expect(screen.getByText('내 순위 5위')).toBeInTheDocument();
    expect(screen.getByText('57 피해')).toBeInTheDocument();
    expect(screen.getByText('내 순위 5위').closest('p')).toHaveClass(
      'lg:px-3',
      'lg:py-3',
      'lg:text-base',
    );

    const collapseButton = screen.getByRole('button', { name: '접기' });
    expect(collapseButton).toHaveAttribute('aria-expanded', 'true');
    await user.click(collapseButton);

    expect(screen.queryByRole('list', { name: '실시간 기여도 미리보기' })).not.toBeInTheDocument();
    expect(screen.getByLabelText('실시간 순위 갱신 중')).toBeInTheDocument();
    const expandButton = screen.getByRole('button', { name: '펼치기' });
    expect(expandButton).toHaveAttribute('aria-expanded', 'false');

    await user.click(expandButton);
    expect(screen.getByRole('list', { name: '실시간 기여도 미리보기' })).toBeInTheDocument();
  });

  it('상위 기여자 목록을 순위·닉네임·누적 데미지와 함께 보여준다', async () => {
    renderLeaderboard();

    const list = await screen.findByRole('list', { name: '기여도 순위 목록' });
    const rows = screen.getAllByRole('listitem');

    expect(list).toBeInTheDocument();
    expect(rows).toHaveLength(2);
    expect(rows[0]).toHaveTextContent('문장수집가');
    expect(rows[0]).toHaveTextContent('380');
    expect(rows[1]).toHaveTextContent('달빛서기');
    expect(rows[1]).toHaveTextContent('240');
  });

  it('내가 상위 목록 밖이면 내 순위를 별도 영역에 고정 표시한다', async () => {
    renderLeaderboard({ myUserId: 99 });

    expect(await screen.findByText('내 순위 5위')).toBeInTheDocument();
    expect(screen.getByText('누적 데미지 57')).toBeInTheDocument();
  });

  it('사용자 판별이 끝나기 전에는 내 순위 고정 영역을 그리지 않는다', async () => {
    // 판별 전에는 목록 안/밖을 알 수 없다. me만 보고 그리면 목록 안 사용자에게
    // 강조 없는 행 + 고정 영역이 중복으로 떴다가 사라지는 깜빡임이 생긴다.
    renderLeaderboard({ myUserId: null, isMyUserPending: true });

    await screen.findByRole('list', { name: '기여도 순위 목록' });

    expect(screen.queryByText(/내 순위/)).not.toBeInTheDocument();
  });

  it('내가 목록에 있으면 행을 강조하고 별도 영역은 보이지 않는다', async () => {
    renderLeaderboard({ myUserId: 12 });

    const list = await screen.findByRole('list', { name: '기여도 순위 목록' });
    expect(list).toHaveTextContent('나');
    // 목록에서 이미 확인되므로 고정 영역을 중복으로 두지 않는다.
    expect(screen.queryByText(/내 순위/)).not.toBeInTheDocument();
  });

  it('leaderboard 스냅숏이 캐시를 대체하면 순위 역전이 새로고침 없이 반영된다', async () => {
    const { queryClient } = renderLeaderboard({ myUserId: null });
    await screen.findByRole('list', { name: '기여도 순위 목록' });

    // SSE 수신 경로(useBossRunStream)가 하는 일과 동일한 캐시 대체다.
    act(() => {
      queryClient.setQueryData<BossRunLeaderboardResponse>(
        bossQueryKeys.leaderboard(BOSS_RUN_ID),
        (current) =>
          current && {
            ...current,
            entries: [
              { rank: 1, userId: 12, nickname: '달빛서기', totalDamage: 460 },
              { rank: 2, userId: 11, nickname: '문장수집가', totalDamage: 380 },
            ],
          },
      );
    });

    // 캐시 알림은 마이크로태스크로 전파되므로 리렌더를 기다린 뒤 확인한다.
    await waitFor(() => {
      const rows = screen.getAllByRole('listitem');
      expect(rows[0]).toHaveTextContent('달빛서기');
      expect(rows[0]).toHaveTextContent('460');
      expect(rows[1]).toHaveTextContent('문장수집가');
    });
  });

  it('참여자가 없으면 빈 상태 안내를 보여준다', async () => {
    server.use(
      http.get('*/api/v1/boss-runs/:bossRunId/leaderboard', () =>
        HttpResponse.json({ bossRunId: BOSS_RUN_ID, entries: [], me: null }),
      ),
    );

    renderLeaderboard();

    expect(await screen.findByText(/아직 제출한 참가자가 없어요/)).toBeInTheDocument();
  });

  it('조회에 실패하면 섹션 안에서 오류를 안내하고 다시 시도할 수 있다', async () => {
    let failCount = 0;
    server.use(
      http.get('*/api/v1/boss-runs/:bossRunId/leaderboard', () => {
        failCount += 1;
        if (failCount === 1) {
          return HttpResponse.json({ code: 'INTERNAL_ERROR' }, { status: 500 });
        }
        return HttpResponse.json({
          bossRunId: BOSS_RUN_ID,
          entries: [{ rank: 1, userId: 11, nickname: '문장수집가', totalDamage: 380 }],
          me: null,
        });
      }),
    );
    const user = userEvent.setup();

    renderLeaderboard();

    expect(await screen.findByText('기여도 순위를 불러오지 못했습니다.')).toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: '다시 시도' }));

    expect(await screen.findByRole('list', { name: '기여도 순위 목록' })).toBeInTheDocument();
  });
});
