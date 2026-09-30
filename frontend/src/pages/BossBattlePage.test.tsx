import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { act, render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { HttpResponse, http } from 'msw';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { beforeAll, describe, expect, it, vi } from 'vitest';

import { BossRunStreamContext } from '@/features/boss';
import type {
  BossRunLeaderboardStreamEvent,
  BossRunStreamEvent,
  BossRunStreamHandlers,
  EligibleSentenceResponse,
} from '@/features/boss';
import { server } from '@/mocks/server';

import type { PageResponse } from '@/shared/api';

import { BossBattlePage } from './BossBattlePage';

beforeAll(() => {
  HTMLDialogElement.prototype.showModal = function showModal() {
    this.open = true;
  };
  HTMLDialogElement.prototype.close = function close() {
    this.open = false;
  };
});

// 제출 이력은 탭 안에 있어 열어야 그려진다.
async function openHistoryTab(user: ReturnType<typeof userEvent.setup>) {
  await user.click(await screen.findByRole('tab', { name: '내 제출 이력' }));
}

function renderBossBattlePage(path = '/boss/7/battle') {
  const queryClient = new QueryClient({
    defaultOptions: {
      queries: {
        retry: false,
      },
    },
  });

  render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter initialEntries={[path]}>
        <Routes>
          <Route path="/boss/:bossRunId/battle" element={<BossBattlePage />} />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

describe('BossBattlePage', () => {
  it('제출 가능한 문장을 조회하고 하나를 선택한다', async () => {
    const user = userEvent.setup();

    renderBossBattlePage();

    expect(
      await screen.findByRole('heading', {
        name: '새벽의 수호자',
        level: 1,
      }),
    ).toBeInTheDocument();
    const bossImage = screen.getByLabelText('보스 정보').querySelector('img');
    expect(bossImage).toHaveClass('lg:size-64');
    expect(screen.getByLabelText('보스 정보').parentElement).toHaveClass(
      'lg:grid-cols-[minmax(0,1fr)_320px]',
    );
    expect(screen.getByRole('heading', { name: '새벽의 수호자', level: 1 })).toHaveClass(
      'lg:text-3xl',
    );

    expect(screen.getByText('HP 62%')).toBeInTheDocument();

    const hpProgressBar = screen.getByRole('progressbar', {
      name: '보스 HP',
    });

    expect(hpProgressBar).toHaveAttribute('aria-valuemax', '100');
    expect(hpProgressBar).toHaveAttribute('aria-valuenow', '62');
    expect(hpProgressBar).toHaveAttribute('aria-valuetext', 'HP 62%');

    // 상세 화면에서 넘어온 원정 요약도 함께 보인다.
    expect(screen.getByText(/^Phase \d+$/)).toBeInTheDocument();
    expect(screen.getByText('최소 점수')).toBeInTheDocument();
    expect(screen.getByText('권장 점수')).toBeInTheDocument();
    expect(screen.getByText('남은 제출')).toBeInTheDocument();
    const bonusMoods = screen.getByLabelText('원정 분위기와 약점');
    expect(bonusMoods).toHaveClass('lg:text-base');
    expect(within(bonusMoods).getByText('분위기')).toBeInTheDocument();
    expect(within(bonusMoods).getByText('평온')).toBeInTheDocument();
    expect(within(bonusMoods).getByText('약점')).toBeInTheDocument();
    expect(within(bonusMoods).getByText('기쁨')).toBeInTheDocument();
    expect(within(bonusMoods).queryByText(/보너스|1\.2|가중치|계산식/)).not.toBeInTheDocument();

    const scoreGuide = screen.getByLabelText('문장 제출 점수 기준');
    expect(scoreGuide).toHaveClass('lg:text-base');
    expect(within(scoreGuide).getByText('최소 점수')).toBeInTheDocument();
    expect(within(scoreGuide).getByText('20')).toBeInTheDocument();
    expect(within(scoreGuide).getByText('권장 점수')).toBeInTheDocument();
    expect(within(scoreGuide).getByText('35')).toBeInTheDocument();

    expect(screen.getByText('문장 에너지 높은 순 · 총 2개')).toHaveClass('lg:text-base');
    expect(screen.getByRole('heading', { name: '제출 전 문장' })).toBeInTheDocument();
    expect(screen.queryByText(/버전 \d+/)).not.toBeInTheDocument();
    expect(screen.queryByText('선택하기')).not.toBeInTheDocument();

    // 전투 화면에서는 오늘의 단어를 보여주지 않는다.
    expect(screen.queryByRole('heading', { name: '오늘의 단어' })).not.toBeInTheDocument();

    // 문장 분위기 해시태그는 서고 상세 조회로 비동기 표시된다.
    expect(await screen.findByText('#평온')).toHaveClass('lg:px-3', 'lg:py-1', 'lg:text-sm');
    expect(
      screen.getByRole('list', {
        name: '제출 가능한 문장 목록',
      }),
    ).toBeInTheDocument();

    const firstCard = screen.getByRole('button', {
      name: /햇빛이 창가에 부드럽게 번졌다/,
    });

    const secondCard = screen.getByRole('button', {
      name: /고요한 바람이 숲의 이야기를 전해 주었다/,
    });

    const submitButton = screen.getByRole('button', {
      name: '이 문장으로 제출하기',
    });

    expect(firstCard).toHaveAttribute('aria-pressed', 'false');
    expect(secondCard).toHaveAttribute('aria-pressed', 'false');
    expect(submitButton).toBeDisabled();

    await user.click(firstCard);

    expect(firstCard).toHaveAttribute('aria-pressed', 'true');
    expect(secondCard).toHaveAttribute('aria-pressed', 'false');
    expect(submitButton).toBeEnabled();

    await user.click(secondCard);

    expect(firstCard).toHaveAttribute('aria-pressed', 'false');
    expect(secondCard).toHaveAttribute('aria-pressed', 'true');

    const scrollIntoView = vi.fn();
    Object.defineProperty(screen.getByLabelText('보스 정보'), 'scrollIntoView', {
      configurable: true,
      value: scrollIntoView,
    });

    // 이용동의 팝업 없이 바로 제출된다. 동의는 회원가입에서 받는다.
    await user.click(submitButton);

    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
    expect(scrollIntoView).toHaveBeenCalledWith({ behavior: 'auto', block: 'start' });

    expect(
      await screen.findByRole('status', {
        name: '55 데미지',
      }),
    ).toBeInTheDocument();
    expect(screen.queryByRole('heading', { name: '55 데미지!' })).not.toBeInTheDocument();
    expect(screen.queryByText('공격 계산 상세')).not.toBeInTheDocument();
    expect(screen.getByRole('button', { name: '이 문장으로 제출하기' })).toBeDisabled();

    await waitFor(() =>
      expect(screen.getByText('문장 에너지 높은 순 · 총 1개')).toBeInTheDocument(),
    );
    expect(screen.getByText('햇빛이 창가에 부드럽게 번졌다.')).toBeInTheDocument();
    expect(screen.queryByText('고요한 바람이 숲의 이야기를 전해 주었다.')).not.toBeInTheDocument();
  });

  it('이력 탭에서는 제출 영역을 감추고 문장 탭으로 돌아오면 선택이 유지된다', async () => {
    const user = userEvent.setup();
    renderBossBattlePage();

    await user.click(await screen.findByRole('button', { name: /햇빛이 창가에/ }));

    const submitButton = screen.getByRole('button', { name: '이 문장으로 제출하기' });
    expect(submitButton).toBeEnabled();

    await openHistoryTab(user);

    // 고른 문장이 보이지 않는 탭에서는 제출 영역을 노출하지 않는다.
    expect(screen.queryByRole('button', { name: '이 문장으로 제출하기' })).not.toBeInTheDocument();

    await user.click(screen.getByRole('tab', { name: '제출 전 문장' }));

    expect(screen.getByRole('button', { name: /햇빛이 창가에/ })).toHaveAttribute(
      'aria-pressed',
      'true',
    );
    expect(screen.getByRole('button', { name: '이 문장으로 제출하기' })).toBeEnabled();
  });

  it('제출 전 문장과 제출 이력을 탭으로 나눠 보여준다', async () => {
    const user = userEvent.setup();
    renderBossBattlePage();

    expect(await screen.findByRole('heading', { name: '제출 전 문장' })).toBeInTheDocument();
    // 이력은 다른 탭에 있으므로 처음에는 목록이 그려지지 않는다.
    expect(screen.queryByRole('list', { name: '내 제출 이력 목록' })).not.toBeInTheDocument();

    await openHistoryTab(user);

    expect(screen.getByRole('heading', { name: '내 제출 이력' })).toBeInTheDocument();

    const history = screen.getByRole('list', { name: '내 제출 이력 목록' });
    expect(
      await screen.findByText('달빛이 소담하게 내리는 골목에서 은하수 한 조각을 발견했다.'),
    ).toBeInTheDocument();
    // 목록에는 문장과 자세히 버튼만 두고 피해량·제출 시각은 팝업에서 보여준다.
    expect(history).not.toHaveTextContent('최종 피해');

    await user.click(screen.getByRole('button', { name: /제출 문장 자세히 보기$/ }));

    const dialog = screen.getByRole('dialog', { name: '제출 결과' });
    expect(dialog).toHaveTextContent('최종 피해');
    expect(dialog).toHaveTextContent('57');
    expect(dialog).toHaveTextContent('AI 평가');
    expect(dialog).toHaveTextContent('기본 점수83.35');
    expect(dialog).toHaveTextContent('단어 보너스+5');
    expect(dialog).toHaveTextContent('공격력88.35');
    expect(dialog).toHaveTextContent('완결성82');
    expect(dialog).toHaveTextContent('이미지가 선명하고 문장의 흐름이 자연스러워요.');

    await user.click(screen.getByRole('button', { name: '모달 닫기' }));
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
  });

  it('문장 상세를 불러오지 못해도 제출 이력의 본문은 보인다', async () => {
    // 본문은 목록 응답에 함께 오므로 상세 조회 실패와 무관하다. 상세는 모달을 열 때만
    // 필요하고, 상세 조회가 실패해도 독립적인 평가 응답은 표시한다.
    const user = userEvent.setup();
    server.use(
      http.get('*/api/v1/me/sentences/:sentenceVersionId', () =>
        HttpResponse.json({ code: 'NOT_FOUND' }, { status: 404 }),
      ),
    );

    renderBossBattlePage();
    await openHistoryTab(user);

    expect(
      await screen.findByText('달빛이 소담하게 내리는 골목에서 은하수 한 조각을 발견했다.'),
    ).toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: /제출 문장 자세히 보기$/ }));

    // 팝업은 결과만 다루고 문장 원문은 목록이 계속 보여 준다.
    const dialog = screen.getByRole('dialog', { name: '제출 결과' });
    expect(dialog).not.toHaveTextContent('달빛이 소담하게 내리는 골목에서');
    expect(
      await screen.findByText('이미지가 선명하고 문장의 흐름이 자연스러워요.'),
    ).toBeInTheDocument();
    expect(dialog).toHaveTextContent('기본 점수83.35');
    expect(dialog).toHaveTextContent('단어 보너스+5');
    expect(dialog).toHaveTextContent('공격력88.35');
  });

  it('제출 시각이 올바르지 않아도 이력 화면을 유지한다', async () => {
    const user = userEvent.setup();
    server.use(
      http.get('*/api/v1/boss-runs/:bossRunId', () =>
        HttpResponse.json({
          bossRunId: 7,
          bossName: '새벽의 수호자',
          status: 'ACTIVE',
          remainingSubmissionCount: 2,
          currentPhase: { maxHp: 1000, remainingHp: 620 },
          mySubmissions: [
            {
              submissionId: 55,
              sentenceVersionId: 84,
              content: '시간 정보가 잘못된 제출 문장',
              finalDamage: 57,
              submittedAt: 'invalid-date',
            },
          ],
        }),
      ),
    );

    renderBossBattlePage();
    await openHistoryTab(user);

    // 잘못된 시각 때문에 목록이 깨지지 않고, 대체 문구는 팝업에서 확인된다.
    expect(await screen.findByText('시간 정보가 잘못된 제출 문장')).toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: /제출 문장 자세히 보기$/ }));

    const dialog = screen.getByRole('dialog', { name: '제출 결과' });
    expect(dialog).toHaveTextContent('제출 시각 정보 없음');
    expect(dialog).toHaveTextContent('57');
  });

  it('제출 이력이 없으면 빈 상태를 표시한다', async () => {
    const user = userEvent.setup();
    server.use(
      http.get('*/api/v1/boss-runs/:bossRunId', () =>
        HttpResponse.json({
          bossRunId: 7,
          bossName: '새벽의 수호자',
          status: 'ACTIVE',
          remainingSubmissionCount: 2,
          currentPhase: { maxHp: 1000, remainingHp: 620 },
          mySubmissions: [],
        }),
      ),
    );

    renderBossBattlePage();
    await openHistoryTab(user);

    expect(await screen.findByText('아직 이 원정에 제출한 문장이 없어요.')).toBeInTheDocument();
    expect(screen.queryByRole('list', { name: '내 제출 이력 목록' })).not.toBeInTheDocument();
  });

  it('정책 거절 오류를 한국어로 안내한다', async () => {
    const user = userEvent.setup();
    server.use(
      http.post('*/api/v1/boss-runs/:bossRunId/submissions', () =>
        HttpResponse.json(
          {
            occurredAt: new Date().toISOString(),
            code: 'POLICY_REJECTED',
            message: 'Base score is below the hard minimum.',
            fieldErrors: [],
          },
          { status: 422 },
        ),
      ),
    );

    renderBossBattlePage();
    await user.click(await screen.findByRole('button', { name: /햇빛이 창가에/ }));
    await user.click(screen.getByRole('button', { name: '이 문장으로 제출하기' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('제출 정책을 만족하지 못했습니다');
  });

  it('인증이 만료되면 반복 제출 대신 로그인 이동을 안내한다', async () => {
    const user = userEvent.setup();
    server.use(
      http.post('*/api/v1/boss-runs/:bossRunId/submissions', () =>
        HttpResponse.json(
          {
            occurredAt: new Date().toISOString(),
            code: 'UNAUTHORIZED',
            message: 'Authentication is required.',
            fieldErrors: [],
          },
          { status: 401 },
        ),
      ),
    );

    renderBossBattlePage();
    await user.click(await screen.findByRole('button', { name: /햇빛이 창가에/ }));
    await user.click(screen.getByRole('button', { name: '이 문장으로 제출하기' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('로그인이 만료됐습니다');
    expect(screen.queryByRole('button', { name: '이 문장으로 제출하기' })).not.toBeInTheDocument();
    expect(screen.getByRole('link', { name: '로그인하기' })).toHaveAttribute('href', '/login');
  });

  it('제출 실패 시 제출 영역에서 오류를 안내하고 다시 시도할 수 있다', async () => {
    const user = userEvent.setup();

    server.use(
      http.post('*/api/v1/boss-runs/:bossRunId/submissions', () =>
        HttpResponse.json(
          {
            occurredAt: new Date().toISOString(),
            code: 'CONFLICT',
            message: 'The request conflicts with the current state.',
            fieldErrors: [],
          },
          { status: 409 },
        ),
      ),
    );

    renderBossBattlePage();

    await user.click(
      await screen.findByRole('button', {
        name: /햇빛이 창가에 부드럽게 번졌다/,
      }),
    );
    await user.click(screen.getByRole('button', { name: '이 문장으로 제출하기' }));

    expect(await screen.findByRole('alert')).toHaveTextContent(
      '원정 상태가 변경됐거나 이미 제출한 문장입니다',
    );
    // 팝업 없이 제출 버튼이 그대로 남아 바로 다시 시도할 수 있다.
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
    expect(screen.getByRole('button', { name: '이 문장으로 제출하기' })).toBeEnabled();

    // 다른 문장을 고르면 이전 실패 안내는 사라진다.
    await user.click(
      screen.getByRole('button', { name: /고요한 바람이 숲의 이야기를 전해 주었다/ }),
    );

    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
  });

  it('마지막 일격으로 클리어되면 상세 재조회 후에도 데미지 결과를 표시한다', async () => {
    const user = userEvent.setup();
    let isCleared = false;
    let detailRequestCount = 0;

    server.use(
      http.get('*/api/v1/boss-runs/:bossRunId', () => {
        detailRequestCount += 1;

        return HttpResponse.json({
          bossRunId: 7,
          bossId: 3,
          bossName: '새벽의 수호자',
          bossDescription: '새벽이 끝나기 전에 마음을 모으세요.',
          status: isCleared ? 'CLEARED' : 'ACTIVE',
          startedAt: '2026-07-31T00:00:00Z',
          endsAt: '2026-08-01T00:00:00Z',
          clearedAt: isCleared ? '2026-08-04T01:00:00Z' : null,
          accumulatedDamage: isCleared ? 1000 : 380,
          currentPhase: {
            phaseId: 21,
            phaseNo: 1,
            maxHp: 1000,
            remainingHp: isCleared ? 0 : 620,
            progressPercent: isCleared ? 100 : 38,
            hardMinScore: 20,
            recommendedScore: 35,
            damageMultiplier: 1.25,
            status: isCleared ? 'CLEARED' : 'ACTIVE',
          },
          moods: [],
          mySubmissionCount: isCleared ? 2 : 1,
          remainingSubmissionCount: isCleared ? 1 : 2,
          mySubmissions: [],
        });
      }),
      http.post('*/api/v1/boss-runs/:bossRunId/submissions', () => {
        isCleared = true;

        return HttpResponse.json(
          {
            submissionId: 57,
            bossRunId: 7,
            bossPhaseId: 21,
            serviceDate: '2026-08-04',
            damage: {
              aiBaseScore: 39,
              wordBonus: 0,
              moodBonus: 2,
              weaknessBonus: 3,
              phaseModifier: 1.25,
              finalDamage: 620,
            },
            phase: { phaseNo: 1, remainingHp: 0, status: 'CLEARED' },
            bossRunStatus: 'CLEARED',
          },
          { status: 201 },
        );
      }),
    );

    renderBossBattlePage();

    await user.click(
      await screen.findByRole('button', {
        name: /햇빛이 창가에 부드럽게 번졌다/,
      }),
    );
    await user.click(screen.getByRole('button', { name: '이 문장으로 제출하기' }));

    expect(await screen.findByRole('status', { name: '620 데미지' })).toBeInTheDocument();

    await waitFor(() => expect(detailRequestCount).toBeGreaterThanOrEqual(2));

    expect(screen.getByRole('status', { name: '620 데미지' })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: '클리어 결과 보기' })).toHaveAttribute(
      'href',
      '/boss/7/clear',
    );
    expect(
      screen.queryByRole('heading', { name: '지금은 문장을 제출할 수 없는 원정이에요' }),
    ).not.toBeInTheDocument();
  });

  it('제출 가능한 문장이 없으면 빈 상태를 표시한다', async () => {
    server.use(
      http.get('*/api/v1/boss-runs/:bossRunId/eligible-sentences', () =>
        HttpResponse.json({
          content: [],
          page: 0,
          size: 10,
          totalElements: 0,
          totalPages: 0,
          hasNext: false,
        }),
      ),
    );

    renderBossBattlePage();

    expect(await screen.findByText('제출 가능한 문장이 없어요')).toBeInTheDocument();

    expect(screen.getByText('문장 에너지 높은 순 · 총 0개')).toBeInTheDocument();
  });

  it('ACTIVE 상태가 아닌 보스전이면 원정 정보는 보여주되 문장 선택을 차단한다', async () => {
    renderBossBattlePage('/boss/8/battle');

    // 상세 화면이 통합되어 제출 불가 상태에서도 원정 정보(HP·요약)는 계속 보인다.
    expect(await screen.findByText('아직 시작 전인 원정입니다', { exact: false })).toBeVisible();
    expect(screen.getByRole('progressbar', { name: '보스 HP' })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: '원정 목록으로 돌아가기' })).toHaveAttribute(
      'href',
      '/boss',
    );
    expect(screen.queryByRole('link', { name: '클리어 결과 보기' })).not.toBeInTheDocument();
    expect(screen.queryByRole('list', { name: '제출 가능한 문장 목록' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: '이 문장으로 제출하기' })).not.toBeInTheDocument();
  });

  it('남은 제출이 없는 회원도 원정 진행 상황과 제출 이력을 계속 본다', async () => {
    server.use(
      http.get('*/api/v1/boss-runs/:bossRunId', () =>
        HttpResponse.json({
          bossRunId: 7,
          bossName: '새벽의 수호자',
          status: 'ACTIVE',
          remainingSubmissionCount: 0,
          mySubmissionCount: 3,
          currentPhase: { maxHp: 1000, remainingHp: 620 },
          mySubmissions: [
            {
              submissionId: 55,
              sentenceVersionId: 84,
              content: '오늘 마지막으로 제출한 문장',
              finalDamage: 57,
              submittedAt: '2026-07-30T10:00:00Z',
            },
          ],
        }),
      ),
    );

    renderBossBattlePage();

    // 제출 UI 대신 안내가 뜨고, HP와 이력은 계속 보인다.
    expect(
      await screen.findByText('오늘 제출할 수 있는 횟수를 모두 사용했어요.', { exact: false }),
    ).toBeVisible();
    expect(screen.getByRole('progressbar', { name: '보스 HP' })).toBeInTheDocument();
    expect(screen.getByText('오늘 마지막으로 제출한 문장')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: '이 문장으로 제출하기' })).not.toBeInTheDocument();
    expect(screen.queryByRole('tab', { name: '제출 전 문장' })).not.toBeInTheDocument();
  });

  it('잘못된 보스전 ID이면 주소 오류를 표시한다', () => {
    renderBossBattlePage('/boss/not-a-number/battle');

    expect(screen.getByRole('alert')).toHaveTextContent('잘못된 원정 주소입니다');
  });

  it('다음 페이지의 제출 가능 문장을 조회한다', async () => {
    const user = userEvent.setup();

    const firstSentence: EligibleSentenceResponse = {
      sentenceVersionId: 84,
      sentenceId: 42,
      versionNo: 2,
      content: '햇빛이 창가에 부드럽게 번졌다.',
      baseScore: 39,
      wordBonus: 0,
      gameBaseAttack: 39,
      sentenceEnergy: 55,
    };

    const secondSentence: EligibleSentenceResponse = {
      sentenceVersionId: 91,
      sentenceId: 47,
      versionNo: 1,
      content: '고요한 바람이 숲의 이야기를 전해 주었다.',
      baseScore: 42,
      wordBonus: 0,
      gameBaseAttack: 42,
      sentenceEnergy: 42,
    };

    server.use(
      http.get('*/api/v1/boss-runs/:bossRunId/eligible-sentences', ({ request }) => {
        const url = new URL(request.url);
        const page = Number(url.searchParams.get('page') ?? '0');

        const response: PageResponse<EligibleSentenceResponse> = {
          content: page === 0 ? [firstSentence] : [secondSentence],
          page,
          size: 10,
          totalElements: 2,
          totalPages: 2,
          hasNext: page === 0,
        };

        return HttpResponse.json(response);
      }),
    );

    renderBossBattlePage();

    expect(await screen.findByText(firstSentence.content)).toBeInTheDocument();

    expect(screen.getByText('1 / 2')).toBeInTheDocument();

    await user.click(
      screen.getByRole('button', {
        name: /햇빛이 창가에 부드럽게 번졌다/,
      }),
    );

    expect(
      screen.getByRole('button', {
        name: '이 문장으로 제출하기',
      }),
    ).toBeEnabled();

    await user.click(
      screen.getByRole('button', {
        name: '다음',
      }),
    );

    expect(await screen.findByText(secondSentence.content)).toBeInTheDocument();

    expect(screen.getByText('2 / 2')).toBeInTheDocument();

    expect(
      screen.getByRole('button', {
        name: '이 문장으로 제출하기',
      }),
    ).toBeDisabled();

    expect(
      screen.getByRole('button', {
        name: '다음',
      }),
    ).toBeDisabled();
  });

  it('현재 페이지가 전체 페이지 범위를 벗어나면 마지막 페이지로 이동한다', async () => {
    const user = userEvent.setup();
    let secondPageRequested = false;

    const sentence: EligibleSentenceResponse = {
      sentenceVersionId: 84,
      sentenceId: 42,
      versionNo: 2,
      content: '범위가 줄어도 다시 찾을 수 있는 문장입니다.',
      baseScore: 39,
      wordBonus: 0,
      gameBaseAttack: 39,
      sentenceEnergy: 55,
    };

    server.use(
      http.get('*/api/v1/boss-runs/:bossRunId/eligible-sentences', ({ request }) => {
        const page = Number(new URL(request.url).searchParams.get('page') ?? '0');

        if (page === 1) {
          secondPageRequested = true;

          return HttpResponse.json({
            content: [],
            page: 1,
            size: 10,
            totalElements: 1,
            totalPages: 1,
            hasNext: false,
          });
        }

        return HttpResponse.json({
          content: [sentence],
          page: 0,
          size: 10,
          totalElements: secondPageRequested ? 1 : 2,
          totalPages: secondPageRequested ? 1 : 2,
          hasNext: !secondPageRequested,
        });
      }),
    );

    renderBossBattlePage();

    expect(await screen.findByText(sentence.content)).toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: '다음' }));

    await waitFor(() => {
      expect(
        screen.queryByRole('navigation', { name: '제출 가능 문장 페이지' }),
      ).not.toBeInTheDocument();
    });

    expect(await screen.findByText(sentence.content)).toBeInTheDocument();
  });

  it('마지막 페이지의 마지막 문장을 제출해 페이지 수가 줄어도 결과를 유지한다', async () => {
    const user = userEvent.setup();
    let submittedSentenceVersionId: number | null = null;

    const sentences: EligibleSentenceResponse[] = Array.from({ length: 11 }, (_, index) => ({
      sentenceVersionId: 100 + index,
      sentenceId: 200 + index,
      versionNo: 1,
      content: `페이지 범위 테스트 문장 ${index + 1}`,
      baseScore: 40,
      wordBonus: 1,
      gameBaseAttack: 13,
      sentenceEnergy: 13 + index,
    }));

    server.use(
      http.get('*/api/v1/boss-runs/:bossRunId/eligible-sentences', ({ request }) => {
        const url = new URL(request.url);
        const page = Number(url.searchParams.get('page') ?? '0');
        const size = Number(url.searchParams.get('size') ?? '10');
        const availableSentences = sentences.filter(
          (sentence) => sentence.sentenceVersionId !== submittedSentenceVersionId,
        );
        const startIndex = page * size;
        const endIndex = startIndex + size;

        return HttpResponse.json({
          content: availableSentences.slice(startIndex, endIndex),
          page,
          size,
          totalElements: availableSentences.length,
          totalPages: Math.ceil(availableSentences.length / size),
          hasNext: endIndex < availableSentences.length,
        });
      }),
      http.post('*/api/v1/boss-runs/:bossRunId/submissions', async ({ request }) => {
        const body = (await request.json()) as {
          sentenceVersionId: number;
          submissionConsentAccepted: boolean;
        };
        submittedSentenceVersionId = body.sentenceVersionId;

        return HttpResponse.json(
          {
            submissionId: 99,
            bossRunId: 7,
            bossPhaseId: 21,
            serviceDate: '2026-08-04',
            damage: {
              aiBaseScore: 40,
              wordBonus: 1,
              moodBonus: 2,
              weaknessBonus: 3,
              phaseModifier: 1.25,
              finalDamage: 64,
            },
            phase: { phaseNo: 1, remainingHp: 556, status: 'ACTIVE' },
            bossRunStatus: 'ACTIVE',
          },
          { status: 201 },
        );
      }),
    );

    renderBossBattlePage();

    expect(await screen.findByText('페이지 범위 테스트 문장 1')).toBeInTheDocument();
    expect(screen.getByText('1 / 2')).toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: '다음' }));

    const lastSentenceButton = await screen.findByRole('button', {
      name: /페이지 범위 테스트 문장 11/,
    });
    expect(screen.getByText('2 / 2')).toBeInTheDocument();

    await user.click(lastSentenceButton);
    await user.click(screen.getByRole('button', { name: '이 문장으로 제출하기' }));

    expect(submittedSentenceVersionId).toBe(110);
    expect(await screen.findByRole('status', { name: '64 데미지' })).toBeInTheDocument();

    await waitFor(() => {
      expect(
        screen.queryByRole('navigation', { name: '제출 가능 문장 페이지' }),
      ).not.toBeInTheDocument();
    });

    expect(screen.getByText('페이지 범위 테스트 문장 1')).toBeInTheDocument();
    expect(screen.getByRole('status', { name: '64 데미지' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '이 문장으로 제출하기' })).toBeDisabled();
  });

  it('최대 HP가 0이면 HP 비율을 0%로 표시한다', async () => {
    server.use(
      http.get('*/api/v1/boss-runs/:bossRunId', () =>
        HttpResponse.json({
          bossRunId: 7,
          bossName: '새벽의 수호자',
          status: 'ACTIVE',
          currentPhase: {
            maxHp: 0,
            remainingHp: 0,
          },
          mySubmissions: [],
        }),
      ),
    );

    renderBossBattlePage();

    expect(await screen.findByText('HP 0%')).toBeInTheDocument();
    expect(screen.getByRole('progressbar', { name: '보스 HP' })).toHaveAttribute(
      'aria-valuemax',
      '100',
    );
    expect(screen.getByRole('progressbar', { name: '보스 HP' })).toHaveAttribute(
      'aria-valuenow',
      '0',
    );
  });

  it('CLEARED 상태면 문장 선택을 차단하고 클리어 화면으로 안내한다', async () => {
    server.use(
      http.get('*/api/v1/boss-runs/:bossRunId', () =>
        HttpResponse.json({
          bossRunId: 7,
          bossName: '새벽의 수호자',
          status: 'CLEARED',
          clearedAt: '2026-07-31T12:00:00Z',
          currentPhase: {
            maxHp: 1000,
            remainingHp: 0,
          },
          mySubmissions: [],
        }),
      ),
    );

    renderBossBattlePage();

    expect(
      await screen.findByText('클리어된 원정에는 새 문장을 제출할 수 없어요', { exact: false }),
    ).toBeVisible();
    expect(screen.getByRole('link', { name: '클리어 결과 보기' })).toHaveAttribute(
      'href',
      '/boss/7/clear',
    );
    expect(screen.getByRole('link', { name: '원정 목록으로 돌아가기' })).toHaveAttribute(
      'href',
      '/boss',
    );
    expect(screen.queryByRole('list', { name: '제출 가능한 문장 목록' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: '이 문장으로 제출하기' })).not.toBeInTheDocument();
  });
});

describe('BossBattlePage 실시간 반영', () => {
  function renderWithStream() {
    const queryClient = new QueryClient({
      defaultOptions: { queries: { retry: false } },
    });
    let handlers: BossRunStreamHandlers | null = null;

    render(
      <QueryClientProvider client={queryClient}>
        <BossRunStreamContext.Provider
          value={(_bossRunId, streamHandlers) => {
            handlers = streamHandlers;
            return { close: () => undefined };
          }}
        >
          <MemoryRouter initialEntries={['/boss/7/battle']}>
            <Routes>
              <Route path="/boss/:bossRunId/battle" element={<BossBattlePage />} />
            </Routes>
          </MemoryRouter>
        </BossRunStreamContext.Provider>
      </QueryClientProvider>,
    );

    return {
      push: (sequence: number, event: BossRunStreamEvent) =>
        act(() => handlers?.onEvent(sequence, event)),
      pushLeaderboard: (event: BossRunLeaderboardStreamEvent) =>
        act(() => handlers?.onLeaderboard(event)),
      open: () => act(() => handlers?.onOpen()),
    };
  }

  it('다른 참가자의 타격 이벤트가 새로고침 없이 HP와 데미지 연출에 반영된다', async () => {
    const stream = renderWithStream();

    expect(await screen.findByText('HP 62%')).toBeInTheDocument();
    expect(screen.getByText('실시간')).toBeInTheDocument();

    stream.open();
    stream.push(1, {
      bossRunId: 7,
      phaseNo: 1,
      remainingHp: 510,
      maxHp: 1000,
      accumulatedDamage: 490,
      appliedDamage: 110,
      baseScore: 84,
      runStatus: 'ACTIVE',
    });

    // 캐시 패치만으로 리렌더되어야 하며 재조회를 기다리지 않는다.
    expect(await screen.findByText('HP 51%')).toBeInTheDocument();
    expect(screen.getByLabelText('110 데미지')).toBeInTheDocument();
  });

  it('페이즈 변경 후 새 최소 점수에 미달한 기존 선택은 제출할 수 없다', async () => {
    const user = userEvent.setup();
    let currentPhaseNo = 1;
    server.use(
      http.get('*/api/v1/boss-runs/:bossRunId', () =>
        HttpResponse.json({
          bossRunId: 7,
          bossId: 3,
          bossName: '새벽의 수호자',
          bossDescription: '',
          status: 'ACTIVE',
          startedAt: '2026-07-31T00:00:00Z',
          endsAt: '2099-12-31T00:00:00Z',
          clearedAt: null,
          accumulatedDamage: 380,
          currentPhase: {
            phaseId: currentPhaseNo === 1 ? 21 : 22,
            phaseNo: currentPhaseNo,
            maxHp: 1_000,
            remainingHp: currentPhaseNo === 1 ? 620 : 1_000,
            progressPercent: currentPhaseNo === 1 ? 38 : 0,
            hardMinScore: currentPhaseNo === 1 ? 20 : 80,
            recommendedScore: currentPhaseNo === 1 ? 35 : 90,
            damageMultiplier: 1.25,
            imageUrl: null,
            status: 'ACTIVE',
          },
          moods: [],
          mySubmissionCount: 1,
          remainingSubmissionCount: 2,
          mySubmissions: [],
        }),
      ),
    );
    const stream = renderWithStream();
    const sentence = await screen.findByRole('button', { name: /햇빛이 창가에/ });
    await user.click(sentence);
    expect(screen.getByRole('button', { name: '이 문장으로 제출하기' })).toBeEnabled();

    stream.open();
    currentPhaseNo = 2;
    stream.push(1, {
      bossRunId: 7,
      phaseNo: 2,
      remainingHp: 1_000,
      maxHp: 1_000,
      accumulatedDamage: 380,
      appliedDamage: 0,
      baseScore: 0,
      runStatus: 'ACTIVE',
    });

    await screen.findByText('80');
    expect(screen.getByRole('button', { name: '이 문장으로 제출하기' })).toBeDisabled();
    expect(sentence).toHaveAttribute('aria-pressed', 'false');
  });

  it('leaderboard 스냅숏이 도착하면 순위와 누적 데미지가 새로고침 없이 갱신된다', async () => {
    const stream = renderWithStream();

    // 첫 조회 응답(문장수집가 1위)이 그려진 뒤 스냅숏으로 역전시킨다.
    const list = await screen.findByRole('list', { name: '기여도 순위 목록' });
    expect(list).toHaveTextContent('문장수집가');

    stream.open();
    stream.pushLeaderboard({
      bossRunId: 7,
      entries: [
        { rank: 1, userId: 12, nickname: '달빛서기', totalDamage: 460 },
        { rank: 2, userId: 11, nickname: '문장수집가', totalDamage: 380 },
      ],
    });

    // 페이지에는 문장 목록 등 다른 목록도 있으므로 순위 목록 안에서만 찾는다.
    // 캐시 알림은 마이크로태스크로 전파되므로 리렌더를 기다린 뒤 확인한다.
    await waitFor(() => {
      const rows = within(list).getAllByRole('listitem');
      expect(rows[0]).toHaveTextContent('달빛서기');
      expect(rows[0]).toHaveTextContent('460');
      expect(rows[1]).toHaveTextContent('문장수집가');
    });
  });

  it('스트림을 지원하지 않는 환경에서는 주기 갱신 배지로 강등을 알린다', async () => {
    // 기본 팩토리는 jsdom(EventSource 부재)에서 null을 반환해 폴링으로 강등된다.
    renderBossBattlePage();

    expect(await screen.findByText('HP 62%')).toBeInTheDocument();
    await waitFor(() => expect(screen.getByText('주기 갱신')).toBeInTheDocument());
  });
});
