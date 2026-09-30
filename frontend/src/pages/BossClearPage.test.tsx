import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, screen } from '@testing-library/react';
import { HttpResponse, http } from 'msw';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { describe, expect, it } from 'vitest';

import type { BossRunResponse } from '@/features/boss';
import { server } from '@/mocks/server';

import { BossClearPage } from './BossClearPage';

function createBossRun(overrides: Partial<BossRunResponse> = {}): BossRunResponse {
  return {
    bossRunId: 7,
    bossId: 3,
    bossName: '새벽의 수호자',
    bossDescription: '새벽이 끝나기 전에 마음을 모으세요.',
    status: 'CLEARED',
    startedAt: '2026-07-31T00:00:00Z',
    endsAt: '2026-08-01T00:00:00Z',
    clearedAt: '2026-07-31T12:00:00Z',
    accumulatedDamage: 1000,
    currentPhase: {
      phaseId: 21,
      phaseNo: 1,
      maxHp: 1000,
      remainingHp: 0,
      progressPercent: 100,
      hardMinScore: 20,
      recommendedScore: 35,
      damageMultiplier: 1.25,
      status: 'CLEARED',
    },
    moods: [{ code: 'CALM', displayName: '평온', role: 'THEME', weight: 1 }],
    mySubmissionCount: 1,
    remainingSubmissionCount: 2,
    mySubmissions: [
      {
        submissionId: 55,
        bossRunId: 7,
        bossName: '새벽의 수호자',
        bossPhaseId: 21,
        sentenceVersionId: 84,
        content: '달빛이 소담하게 내리는 골목에서 은하수 한 조각을 발견했다.',
        finalDamage: 57,
        submittedAt: '2026-07-31T01:30:00Z',
      },
    ],
    ...overrides,
  };
}

function overrideBossRunDetail(bossRun: BossRunResponse) {
  server.use(http.get('*/api/v1/boss-runs/:bossRunId', () => HttpResponse.json(bossRun)));
}

function renderBossClearPage(path = '/boss/7/clear') {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });

  render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter initialEntries={[path]}>
        <Routes>
          <Route path="/boss/:bossRunId/clear" element={<BossClearPage />} />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

describe('BossClearPage', () => {
  it('클리어 메시지, 제출 차단 안내, 참여 문장과 책 생성 상태를 표시한다', async () => {
    overrideBossRunDetail(createBossRun());
    renderBossClearPage();

    expect(await screen.findByRole('heading', { name: '집필 완료' })).toBeInTheDocument();
    expect(screen.getByText('『새벽의 수호자』')).toBeInTheDocument();
    expect(screen.getByText(/새 문장을 제출할 수 없어요/)).toBeInTheDocument();
    expect(screen.getByText(/공동 책 생성이 시작됐습니다/)).toBeInTheDocument();

    expect(screen.getByRole('heading', { name: '나의 베스트 문장' })).toBeInTheDocument();
    expect(screen.getByText('57 데미지')).toBeInTheDocument();

    expect(screen.getByRole('heading', { name: '나의 기여도' })).toBeInTheDocument();
    expect(screen.getByText('제출 횟수').nextElementSibling).toHaveTextContent('1회');
    expect(screen.getByText('총 데미지').nextElementSibling).toHaveTextContent('57');
    // 기여율 = 내 데미지 합(57) ÷ 레이드 누적 데미지(1000)
    expect(screen.getByText('기여율').nextElementSibling).toHaveTextContent('6%');

    expect(screen.getByRole('heading', { name: '내가 참여한 문장' })).toBeInTheDocument();
    expect(screen.getByText('최종 피해 57')).toBeInTheDocument();

    // 율무 담당 BookGenerationStatusPanel이 붙는 자리 (bossRunId 7 = GENERATING mock)
    expect(screen.getByRole('heading', { name: '공동 책 생성 상태' })).toBeInTheDocument();
    expect(
      await screen.findByText('이야기 마법사가 모두의 문장을 엮고 있어요'),
    ).toBeInTheDocument();

    expect(screen.getByRole('link', { name: '원정으로 돌아가기' })).toHaveAttribute(
      'href',
      '/boss',
    );
  });

  it('참여한 문장이 없으면 빈 안내를 표시한다', async () => {
    overrideBossRunDetail(createBossRun({ mySubmissions: [] }));
    renderBossClearPage();

    expect(await screen.findByText('이번 원정에 제출한 문장이 없어요.')).toBeInTheDocument();
  });

  it('아직 클리어되지 않은 레이드면 상세로 돌아가도록 안내한다', async () => {
    overrideBossRunDetail(createBossRun({ status: 'ACTIVE', clearedAt: null }));
    renderBossClearPage();

    expect(
      await screen.findByRole('heading', { name: '아직 클리어되지 않은 원정이에요' }),
    ).toBeInTheDocument();
    expect(screen.getByRole('status')).toHaveTextContent('원정이 아직 진행 중이에요');
    expect(screen.getByRole('link', { name: '원정 상세로 돌아가기' })).toHaveAttribute(
      'href',
      '/boss/7',
    );
  });

  it('레이드 ID가 잘못되면 잘못된 주소 안내를 표시한다', () => {
    renderBossClearPage('/boss/not-a-number/clear');

    expect(screen.getByRole('alert')).toHaveTextContent('잘못된 원정 주소입니다');
  });
});
