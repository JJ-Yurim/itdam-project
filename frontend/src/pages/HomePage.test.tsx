import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { HttpResponse, http } from 'msw';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import type { DailyWordResponse } from '@/features/daily-words';
import { AuthGateProvider } from '@/features/auth';
import { dailyWordsHandlers } from '@/mocks/handlers/dailyWords.handlers';
import { server } from '@/mocks/server';

import { HomePage } from './HomePage';

// 기본 auth mock은 비로그인 상태다. 회원 시나리오는 이 헬퍼로 로그인 상태를 만든다.
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

function renderHomePage() {
  const queryClient = new QueryClient({
    defaultOptions: {
      queries: {
        retry: false,
      },
    },
  });

  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter>
        <AuthGateProvider>
          <HomePage />
        </AuthGateProvider>
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

describe('HomePage', () => {
  beforeEach(() => {
    vi.stubEnv('VITE_API_BASE_URL', 'http://api.test/api');
  });

  afterEach(() => {
    vi.unstubAllEnvs();
  });

  it('회원이면 오늘의 단어 5개와 주요 화면 링크를 표시한다', async () => {
    loginAsMember();
    server.use(
      ...dailyWordsHandlers,
      http.get('*/api/v1/me/boss-runs', () =>
        HttpResponse.json({
          content: [
            {
              bossRunId: 7,
              bossId: 3,
              bossName: '새벽의 수호자',
              status: 'ACTIVE',
              currentPhaseNo: 2,
              startedAt: '2026-07-30T01:00:00Z',
              endsAt: '2099-12-31T00:00:00Z',
              clearedAt: null,
              lastSubmittedAt: '2026-07-30T09:00:00Z',
              bookId: null,
              bookStatus: null,
            },
          ],
          page: 0,
          size: 10,
          totalElements: 1,
          totalPages: 1,
          hasNext: false,
        }),
      ),
    );

    renderHomePage();

    expect(screen.getByText('오늘의 단어를 준비하고 있어요')).toBeInTheDocument();

    expect(await screen.findByText('2026-07-30')).toBeInTheDocument();
    expect(await screen.findByText('7 / 15회')).toBeInTheDocument();

    expect(
      await screen.findByRole('heading', {
        name: '참여중인 원정',
      }),
    ).toBeInTheDocument();

    const raidCard = await screen.findByRole('link', { name: /새벽의 수호자/ });

    expect(raidCard).toHaveAttribute('href', '/boss/7');
    expect(raidCard).toHaveTextContent('Phase 2');
    expect(raidCard).toHaveTextContent('HP 62%');
    expect(screen.getByRole('img', { name: '새벽의 수호자 보스 이미지' })).toHaveAttribute(
      'src',
      'http://api.test/images/boss-dawn-guardian.webp',
    );

    const cards = screen.getAllByRole('button', {
      name: /번째 단어 카드 뒤집기/,
    });

    expect(cards).toHaveLength(5);
    expect(cards[0]).toHaveAccessibleName('1번째 단어 카드 뒤집기');
    expect(cards[4]).toHaveAccessibleName('5번째 단어 카드 뒤집기');

    expect(
      screen.getByRole('link', {
        name: /단어 활용해서 문장 생성하기/,
      }),
    ).toHaveAttribute('href', '/sentences/new');
  });

  it('회원이 참여중인 원정이 없으면 안내와 원정 목록 링크를 표시한다', async () => {
    loginAsMember();
    server.use(
      ...dailyWordsHandlers,
      http.get('*/api/v1/me/boss-runs', () =>
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

    renderHomePage();

    expect(await screen.findByText('아직 참여중인 원정이 없어요')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: '원정 보러 가기' })).toHaveAttribute(
      'href',
      '/boss',
    );
  });

  it('뒤집은 카드는 화면을 다시 열어도 개봉 상태가 유지된다', async () => {
    const user = userEvent.setup();

    loginAsMember();
    server.use(...dailyWordsHandlers);

    const { unmount } = renderHomePage();

    const flipButtons = await screen.findAllByRole('button', { name: /번째 단어 카드 뒤집기/ });
    expect(flipButtons).toHaveLength(5);

    await user.click(screen.getByRole('button', { name: '1번째 단어 카드 뒤집기' }));

    // 다시 방문(리렌더)해도 1번 카드는 열린 채로 복원된다.
    unmount();
    loginAsMember();
    server.use(...dailyWordsHandlers);
    renderHomePage();

    await screen.findByRole('button', { name: '2번째 단어 카드 뒤집기' });
    // 1번 카드는 이미 개봉돼 뒤집기 버튼이 접근성 트리에서 빠지고 뒷면이 보인다.
    expect(screen.getAllByRole('button', { name: /번째 단어 카드 뒤집기/ })).toHaveLength(4);
    expect(screen.getByRole('button', { name: /은하수 의미보기/ })).toBeInTheDocument();
  });

  it('단어 목록이 비어 있으면 빈 상태를 표시한다', async () => {
    const emptyResponse: DailyWordResponse = {
      dailyWordSetId: 12,
      serviceDate: '2026-07-30',
      items: [],
    };

    loginAsMember();
    server.use(http.get('*/api/v1/daily-words/today', () => HttpResponse.json(emptyResponse)));

    renderHomePage();

    expect(await screen.findByText('아직 준비된 단어가 없어요')).toBeInTheDocument();
  });

  it('조회 실패 후 다시 시도할 수 있다', async () => {
    const user = userEvent.setup();
    let requestCount = 0;

    loginAsMember();
    server.use(
      http.get('*/api/v1/daily-words/today', () => {
        requestCount += 1;

        if (requestCount === 1) {
          return HttpResponse.json(
            {
              code: 'INTERNAL_SERVER_ERROR',
              message: '일시적인 오류입니다.',
            },
            { status: 500 },
          );
        }

        return HttpResponse.json({
          dailyWordSetId: 12,
          serviceDate: '2026-07-30',
          items: [],
        } satisfies DailyWordResponse);
      }),
    );

    renderHomePage();

    expect(await screen.findByText('오늘의 단어를 불러오지 못했어요')).toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: '다시 시도' }));

    expect(await screen.findByText('아직 준비된 단어가 없어요')).toBeInTheDocument();

    expect(requestCount).toBe(2);
  });

  it('비회원이면 미리보기 단어를 보여주고 평가 사용량은 표시하지 않는다', async () => {
    renderHomePage();

    const cards = await screen.findAllByRole('button', {
      name: /번째 단어 카드 뒤집기/,
    });

    expect(cards).toHaveLength(5);
    expect(screen.getByText(/문장 만들기는 로그인 후 이용할 수 있어요/)).toBeInTheDocument();
    expect(screen.queryByText(/AI 평가 횟수/)).not.toBeInTheDocument();
    expect(
      screen.getByRole('link', { name: /단어 활용해서 문장 생성하기/ }),
    ).toHaveAttribute('href', '/sentences/new');

    // 비회원은 참여중인 원정 대신 로그인 안내와 원정 목록 링크를 본다.
    expect(screen.getByText('로그인하면 참여중인 원정이 여기에 보여요')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: '원정 구경하러 가기' })).toHaveAttribute(
      'href',
      '/boss',
    );
  });

  it('비회원이 문장 생성 버튼을 누르면 로그인 안내와 선택 버튼을 표시한다', async () => {
    const user = userEvent.setup();

    renderHomePage();

    const createLink = await screen.findByRole('link', {
      name: /단어 활용해서 문장 생성하기/,
    });
    await user.click(createLink);

    expect(screen.getByRole('dialog')).toHaveTextContent('로그인이 필요한 기능이에요');
    expect(screen.getByRole('link', { name: '로그인하러 가기' })).toHaveAttribute(
      'href',
      '/login',
    );

    await user.click(screen.getByRole('button', { name: '다음에 하기' }));

    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
  });

  it('사용자 확인이 401 외 오류로 실패하면 비회원 화면 대신 재시도 안내를 표시한다', async () => {
    server.use(
      http.get('*/api/v1/users/me', () =>
        HttpResponse.json(
          { code: 'INTERNAL_ERROR', message: '일시적인 오류입니다.' },
          { status: 500 },
        ),
      ),
    );

    renderHomePage();

    expect(await screen.findByText('사용자 정보를 확인하지 못했어요')).toBeInTheDocument();
    expect(
      screen.queryByRole('link', { name: /단어 활용해서 문장 생성하기/ }),
    ).not.toBeInTheDocument();
    expect(screen.queryByText(/문장 만들기는 로그인 후 이용할 수 있어요/)).not.toBeInTheDocument();
  });
});
