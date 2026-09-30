import { fireEvent, render, screen, within } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import type { MyBossRunResponse } from '../types';

import { ParticipatingRaidCarousel } from './ParticipatingRaidCarousel';

const FAR_FUTURE = '2099-12-31T00:00:00Z';

type ParticipatingBossRun = MyBossRunResponse & {
  currentPhaseImageUrl?: string | null;
  currentPhaseMaxHp?: number;
  currentPhaseRemainingHp?: number;
};

function createBossRun(overrides: Partial<ParticipatingBossRun> = {}): ParticipatingBossRun {
  return {
    bossRunId: 7,
    bossId: 3,
    bossName: '새벽의 수호자',
    status: 'ACTIVE',
    currentPhaseNo: 2,
    currentPhaseImageUrl: '/images/boss-phases/phase-2.webp',
    currentPhaseMaxHp: 1_000,
    currentPhaseRemainingHp: 930,
    startedAt: '2026-08-01T00:00:00Z',
    endsAt: FAR_FUTURE,
    clearedAt: null,
    lastSubmittedAt: '2026-08-03T01:30:00Z',
    bookId: null,
    bookStatus: null,
    ...overrides,
  };
}

function renderCarousel(bossRuns: MyBossRunResponse[]) {
  render(
    <MemoryRouter>
      <ParticipatingRaidCarousel bossRuns={bossRuns} />
    </MemoryRouter>,
  );
}

describe('ParticipatingRaidCarousel', () => {
  beforeEach(() => {
    vi.stubEnv('VITE_API_BASE_URL', 'http://api.test/api');
  });

  afterEach(() => {
    vi.unstubAllEnvs();
  });

  it('레이드마다 보스 이미지, 이름, Phase를 담은 카드를 표시한다', () => {
    renderCarousel([createBossRun()]);

    const list = screen.getByRole('list', { name: '참여중인 원정 목록' });
    const card = within(list).getByRole('link');

    expect(card).toHaveAttribute('href', '/boss/7');
    expect(card).toHaveTextContent('새벽의 수호자');
    expect(card).toHaveTextContent('Phase 2');
    expect(card).toHaveTextContent('HP 93%');
    const hpBar = within(card).getByRole('progressbar', { name: '새벽의 수호자 남은 HP' });
    expect(hpBar).toHaveAttribute('aria-valuenow', '93');
    expect(hpBar.firstElementChild).toHaveStyle({ width: '93%' });
    expect(within(card).getByRole('img', { name: '새벽의 수호자 보스 이미지' })).toHaveAttribute(
      'src',
      'http://api.test/images/boss-phases/phase-2.webp',
    );
  });

  it('페이즈 이미지가 바뀌면 이전 로드 오류를 초기화하고 새 이미지를 표시한다', () => {
    const { rerender } = render(
      <MemoryRouter>
        <ParticipatingRaidCarousel bossRuns={[createBossRun()]} />
      </MemoryRouter>,
    );

    fireEvent.error(screen.getByRole('img', { name: '새벽의 수호자 보스 이미지' }));
    expect(screen.queryByRole('img')).not.toBeInTheDocument();

    rerender(
      <MemoryRouter>
        <ParticipatingRaidCarousel
          bossRuns={[
            createBossRun({
              currentPhaseNo: 3,
              currentPhaseImageUrl: '/images/boss-phases/phase-3.webp',
            }),
          ]}
        />
      </MemoryRouter>,
    );

    expect(screen.getByRole('img', { name: '새벽의 수호자 보스 이미지' })).toHaveAttribute(
      'src',
      'http://api.test/images/boss-phases/phase-3.webp',
    );
  });

  it('여러 레이드를 순서대로 모두 표시한다', () => {
    renderCarousel([
      createBossRun(),
      createBossRun({ bossRunId: 9, bossName: '잿빛 문지기', currentPhaseNo: 1 }),
    ]);

    const links = screen.getAllByRole('link');

    expect(links).toHaveLength(2);
    expect(links[0]).toHaveAttribute('href', '/boss/7');
    expect(links[1]).toHaveAttribute('href', '/boss/9');
    expect(links[1]).toHaveTextContent('잿빛 문지기');
  });

  it('이미지 URL이 없는 이전 API 응답도 오류 없이 대체 이미지를 표시한다', () => {
    renderCarousel([createBossRun({ currentPhaseImageUrl: undefined })]);

    expect(screen.getByRole('link', { name: /새벽의 수호자/ })).toBeInTheDocument();
    expect(screen.queryByRole('img')).not.toBeInTheDocument();
  });

  it('상대 API base에서는 루트 상대 이미지 경로를 유지한다', () => {
    vi.stubEnv('VITE_API_BASE_URL', '/api');

    renderCarousel([createBossRun()]);

    expect(screen.getByRole('img', { name: '새벽의 수호자 보스 이미지' })).toHaveAttribute(
      'src',
      '/images/boss-phases/phase-2.webp',
    );
  });
});
