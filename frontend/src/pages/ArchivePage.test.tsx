import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { HttpResponse, http } from 'msw';
import { createMemoryRouter, Link, RouterProvider } from 'react-router-dom';
import { beforeAll, describe, expect, it } from 'vitest';

import { server } from '@/mocks/server';
import { MAX_EVALUATION_POLL_FAILURES, sentenceKeys } from '@/features/sentences';
import { ToastProvider } from '@/shared/components';

import { ArchivePage } from './ArchivePage';

beforeAll(() => {
  HTMLDialogElement.prototype.showModal = function showModal() {
    this.open = true;
  };
  HTMLDialogElement.prototype.close = function close() {
    this.open = false;
  };
});

function renderArchivePage(path = '/archive') {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  const router = createMemoryRouter([{ path: '/archive', element: <ArchivePage /> }], {
    initialEntries: [path],
  });

  render(
    <QueryClientProvider client={queryClient}>
      <ToastProvider>
        <RouterProvider router={router} />
      </ToastProvider>
    </QueryClientProvider>,
  );

  return router;
}

describe('ArchivePage', () => {
  it('평가 완료 문장과 상태 정보를 카드로 표시한다', async () => {
    renderArchivePage();

    expect(await screen.findByRole('heading', { name: '내 서고' })).toBeVisible();
    expect(await screen.findByText('총 2개의 문장')).toBeVisible();
    expect(screen.getByText('원정 제출 완료')).toBeVisible();
    expect(screen.getByText('공동 책 수록')).toBeVisible();
    expect(screen.getAllByRole('link', { name: '상세 보기' })).toHaveLength(2);
  });

  it('다음 페이지 버튼으로 page 쿼리를 변경한다', async () => {
    const user = userEvent.setup();
    server.use(
      http.get('*/api/v1/me/sentences', ({ request }) => {
        const page = Number(new URL(request.url).searchParams.get('page') ?? 0);
        return HttpResponse.json({
          content: [],
          page,
          size: 10,
          totalElements: 11,
          totalPages: 2,
          hasNext: page === 0,
        });
      }),
    );
    const router = renderArchivePage();

    await user.click(await screen.findByRole('button', { name: '다음' }));
    expect(router.state.location.search).toBe('?page=2');
  });

  it('문장이 없으면 빈 상태를 표시한다', async () => {
    server.use(
      http.get('*/api/v1/me/sentences', () =>
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
    renderArchivePage();

    expect(await screen.findByText('아직 평가를 완료한 문장이 없어요')).toBeVisible();
  });

  it('목록 조회에 실패하면 오류와 재시도 버튼을 표시한다', async () => {
    server.use(
      http.get('*/api/v1/me/sentences', () =>
        HttpResponse.json({ code: 'INTERNAL_ERROR', message: '조회 실패' }, { status: 500 }),
      ),
    );
    renderArchivePage();

    expect(await screen.findByRole('alert')).toHaveTextContent('내 서고를 불러오지 못했어요');
    expect(screen.getByRole('button', { name: '다시 시도' })).toBeVisible();
  });

  it('미평가된 문장 보기로 전환하면 미평가 문장 목록을 표시한다', async () => {
    const user = userEvent.setup();
    const router = renderArchivePage();

    await screen.findByText('총 2개의 문장');
    await user.click(screen.getByRole('button', { name: '미평가된 문장' }));

    expect(router.state.location.search).toContain('view=unevaluated');
    expect(screen.getByRole('button', { name: '미평가된 문장' })).toHaveAttribute(
      'aria-pressed',
      'true',
    );
    expect(await screen.findByText('고요한 바람이 숲의 이야기를 전해 주었다.')).toBeVisible();
    expect(screen.getAllByText('평가 전')).toHaveLength(2);
    expect(screen.getAllByRole('button', { name: '✨ AI 평가 요청' })).toHaveLength(2);
    // 미평가 카드에는 점수 배지가 없다.
    expect(screen.queryByText(/AI .*점/)).not.toBeInTheDocument();
  });

  it('북마크 카드가 북마크 개수를 표시하고 클릭하면 북마크 목록 화면으로 이동한다', async () => {
    const user = userEvent.setup();
    const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    const router = createMemoryRouter(
      [
        { path: '/archive', element: <ArchivePage /> },
        { path: '/archive/bookmarks', element: <div>북마크 화면</div> },
      ],
      { initialEntries: ['/archive'] },
    );

    render(
      <QueryClientProvider client={queryClient}>
        <RouterProvider router={router} />
      </QueryClientProvider>,
    );

    const bookmarkCard = await screen.findByRole('button', { name: /북마크/ });
    await waitFor(() => expect(bookmarkCard).toHaveTextContent('1개'));

    await user.click(bookmarkCard);

    expect(router.state.location.pathname).toBe('/archive/bookmarks');
    expect(await screen.findByText('북마크 화면')).toBeVisible();
  });

  it('임시 저장 카드가 미평가 문장 개수를 표시하고 클릭하면 미평가 목록으로 이동한다', async () => {
    const user = userEvent.setup();
    const router = renderArchivePage();

    const draftCard = await screen.findByRole('button', { name: /임시 저장/ });
    await waitFor(() => expect(draftCard).toHaveTextContent('2개'));

    await user.click(draftCard);

    expect(router.state.location.search).toContain('tab=sentences');
    expect(router.state.location.search).toContain('view=unevaluated');
    expect(await screen.findByText('고요한 바람이 숲의 이야기를 전해 주었다.')).toBeVisible();
  });

  it('미평가 문장 카드에서 이어 쓰기로 문장 작성 화면 링크를 제공한다', async () => {
    const user = userEvent.setup();
    renderArchivePage();

    await screen.findByText('총 2개의 문장');
    await user.click(screen.getByRole('button', { name: '미평가된 문장' }));

    const continueLinks = await screen.findAllByRole('link', { name: '이어 쓰기' });

    expect(continueLinks).toHaveLength(2);
    expect(continueLinks[0]).toHaveAttribute('href', '/sentences/new?versionId=92');
  });

  it('미평가 문장의 AI 평가를 요청하면 평가 API가 호출된다', async () => {
    const user = userEvent.setup();
    let evaluatedVersionId: string | null = null;

    server.use(
      http.post('*/api/v1/sentence-versions/:versionId/evaluations', ({ params }) => {
        evaluatedVersionId = String(params.versionId);
        return HttpResponse.json({
          sentenceVersionId: Number(params.versionId),
          evaluationStatus: 'EVALUATED',
          safety: { status: 'PASSED', reason: null },
          scores: {
            completeness: 80,
            originality: 75,
            expression: 82,
            imagery: 78,
            emotionalImpact: 81,
            baseScore: 79.2,
          },
          wordBonus: 1,
          gameBaseAttack: 22,
          overallFeedback: null,
          moods: [],
          usedDailyWords: [],
          modelName: 'test',
          promptVersion: 'v1',
          rubricVersion: 'v1',
          evaluatedAt: '2026-07-31T02:00:00Z',
        });
      }),
    );

    renderArchivePage('/archive?view=unevaluated');

    const evaluateButtons = await screen.findAllByRole('button', { name: '✨ AI 평가 요청' });
    await user.click(evaluateButtons[0]);

    await waitFor(() => expect(evaluatedVersionId).toBe('92'));

    // 평가 결과가 팝업으로 표시되고 닫기를 누르면 목록 화면이 유지된다.
    const dialog = await screen.findByRole('dialog');
    expect(dialog).toHaveAccessibleName('AI 평가 결과');
    expect(dialog).toHaveTextContent('79.2');
    expect(dialog).toHaveTextContent('평가가 완료된 문장은 평가된 문장 목록으로 이동해요.');

    await user.click(screen.getByRole('button', { name: '닫기' }));

    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
    expect(screen.getByRole('button', { name: '미평가된 문장' })).toHaveAttribute(
      'aria-pressed',
      'true',
    );
  });

  it('비동기 AI 평가가 끝나면 결과를 표시하고 미평가 목록에서 즉시 제거한다', async () => {
    const user = userEvent.setup();
    let evaluationRequested = false;
    let evaluationCompleted = false;
    let finishEvaluation!: () => void;
    const evaluationGate = new Promise<void>((resolve) => {
      finishEvaluation = resolve;
    });

    const pendingSentence = {
      sentenceVersionId: 92,
      sentenceId: 47,
      versionNo: 1,
      content: '고요한 바람이 숲의 이야기를 전해 주었다.',
      evaluationStatus: 'DRAFT' as const,
      createdAt: '2026-07-31T01:00:00Z',
    };
    const otherSentence = {
      sentenceVersionId: 93,
      sentenceId: 48,
      versionNo: 1,
      content: '마중 나온 강아지가 꼬리로 인사를 건넸다.',
      evaluationStatus: 'DRAFT' as const,
      createdAt: '2026-07-30T22:00:00Z',
    };

    server.use(
      http.get('*/api/v1/me/sentences/unevaluated', ({ request }) => {
        const url = new URL(request.url);
        const page = Number(url.searchParams.get('page') ?? 0);
        const size = Number(url.searchParams.get('size') ?? 10);
        const sentences = evaluationCompleted
          ? [otherSentence]
          : [
              {
                ...pendingSentence,
                evaluationStatus: evaluationRequested
                  ? ('EVALUATING' as const)
                  : ('DRAFT' as const),
              },
              otherSentence,
            ];
        const start = page * size;

        return HttpResponse.json({
          content: sentences.slice(start, start + size),
          page,
          size,
          totalElements: sentences.length,
          totalPages: Math.ceil(sentences.length / size),
          hasNext: start + size < sentences.length,
        });
      }),
      http.post('*/api/v1/sentence-versions/92/evaluations', () => {
        evaluationRequested = true;
        return HttpResponse.json({
          sentenceVersionId: 92,
          evaluationStatus: 'EVALUATING',
          safety: null,
          scores: null,
          wordBonus: null,
          gameBaseAttack: null,
          overallFeedback: null,
          moods: [],
          usedDailyWords: [],
          modelName: null,
          promptVersion: null,
          rubricVersion: null,
          evaluatedAt: null,
        });
      }),
      http.get('*/api/v1/sentence-versions/92/evaluation', async () => {
        await evaluationGate;
        evaluationCompleted = true;
        return HttpResponse.json({
          sentenceVersionId: 92,
          evaluationStatus: 'EVALUATED',
          safety: { status: 'PASSED', reason: null },
          scores: {
            completeness: 80,
            originality: 75,
            expression: 82,
            imagery: 78,
            emotionalImpact: 81,
            baseScore: 79.2,
          },
          wordBonus: 1,
          gameBaseAttack: 22,
          overallFeedback: '문장의 흐름이 자연스러워요.',
          moods: [],
          usedDailyWords: [],
          modelName: 'test',
          promptVersion: 'v1',
          rubricVersion: 'v1',
          evaluatedAt: '2026-07-31T02:00:00Z',
        });
      }),
    );

    renderArchivePage('/archive?view=unevaluated');

    const evaluateButtons = await screen.findAllByRole('button', { name: '✨ AI 평가 요청' });
    await user.click(evaluateButtons[0]);

    expect(await screen.findByRole('button', { name: '평가 중…' })).toBeDisabled();
    expect(screen.getByText('평가 중')).toBeVisible();
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();

    finishEvaluation();

    const dialog = await screen.findByRole('dialog');
    expect(dialog).toHaveTextContent('79.2');
    expect(dialog).not.toHaveTextContent('평가가 진행 중이에요');
    await waitFor(() => {
      expect(
        screen.queryByText('고요한 바람이 숲의 이야기를 전해 주었다.'),
      ).not.toBeInTheDocument();
    });
    expect(screen.getByText('마중 나온 강아지가 꼬리로 인사를 건넸다.')).toBeVisible();
  });

  it('다른 화면에서 평가가 완료되면 30초 캐시가 남아 있어도 복귀 즉시 목록을 갱신한다', async () => {
    const user = userEvent.setup();
    let evaluationCompleted = false;

    server.use(
      http.get('*/api/v1/me/sentences/unevaluated', ({ request }) => {
        const url = new URL(request.url);
        const page = Number(url.searchParams.get('page') ?? 0);
        const size = Number(url.searchParams.get('size') ?? 10);
        const content = evaluationCompleted
          ? []
          : [
              {
                sentenceVersionId: 92,
                sentenceId: 47,
                versionNo: 1,
                content: '다른 화면에 있는 동안 평가가 끝나는 문장',
                evaluationStatus: 'DRAFT' as const,
                createdAt: '2026-07-31T01:00:00Z',
              },
            ];
        const start = page * size;

        return HttpResponse.json({
          content: content.slice(start, start + size),
          page,
          size,
          totalElements: content.length,
          totalPages: Math.ceil(content.length / size),
          hasNext: start + size < content.length,
        });
      }),
    );

    const queryClient = new QueryClient({
      defaultOptions: {
        queries: { retry: false, staleTime: 30_000 },
        mutations: { retry: false },
      },
    });
    const router = createMemoryRouter(
      [
        {
          path: '/archive',
          element: (
            <>
              <Link to="/">메인 화면으로</Link>
              <ArchivePage />
            </>
          ),
        },
        {
          path: '/',
          element: <Link to="/archive?view=unevaluated">미평가 목록으로 돌아가기</Link>,
        },
      ],
      { initialEntries: ['/archive?view=unevaluated'] },
    );

    render(
      <QueryClientProvider client={queryClient}>
        <ToastProvider>
          <RouterProvider router={router} />
        </ToastProvider>
      </QueryClientProvider>,
    );

    expect(await screen.findByText('다른 화면에 있는 동안 평가가 끝나는 문장')).toBeVisible();
    await user.click(screen.getByRole('link', { name: '메인 화면으로' }));

    evaluationCompleted = true;
    await user.click(screen.getByRole('link', { name: '미평가 목록으로 돌아가기' }));

    await waitFor(() => {
      expect(
        screen.queryByText('다른 화면에 있는 동안 평가가 끝나는 문장'),
      ).not.toBeInTheDocument();
    });
  });

  it('평가 상태 조회가 실패 상한에 도달하면 카드 잠금을 해제한다', async () => {
    server.use(
      http.get('*/api/v1/me/sentences/unevaluated', ({ request }) => {
        const url = new URL(request.url);
        const page = Number(url.searchParams.get('page') ?? 0);
        const size = Number(url.searchParams.get('size') ?? 10);
        const content = [
          {
            sentenceVersionId: 92,
            sentenceId: 47,
            versionNo: 1,
            content: '상태 확인에 실패한 문장',
            evaluationStatus: 'EVALUATING',
            createdAt: '2026-07-31T01:00:00Z',
          },
        ];
        const start = page * size;

        return HttpResponse.json({
          content: content.slice(start, start + size),
          page,
          size,
          totalElements: content.length,
          totalPages: Math.ceil(content.length / size),
          hasNext: start + size < content.length,
        });
      }),
      http.get('*/api/v1/sentence-versions/92/evaluation', () =>
        HttpResponse.json({ code: 'INTERNAL_ERROR', message: '조회 실패' }, { status: 500 }),
      ),
    );

    const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    const router = createMemoryRouter([{ path: '/archive', element: <ArchivePage /> }], {
      initialEntries: ['/archive?view=unevaluated'],
    });

    render(
      <QueryClientProvider client={queryClient}>
        <ToastProvider>
          <RouterProvider router={router} />
        </ToastProvider>
      </QueryClientProvider>,
    );

    expect(await screen.findByRole('button', { name: '평가 중…' })).toBeDisabled();
    await waitFor(() => {
      expect(
        queryClient.getQueryState(sentenceKeys.evaluation(92))?.fetchFailureCount,
      ).toBeGreaterThan(0);
    });

    const evaluationQuery = queryClient.getQueryCache().find({
      queryKey: sentenceKeys.evaluation(92),
      exact: true,
    });
    expect(evaluationQuery).toBeDefined();
    evaluationQuery!.setState({
      fetchFailureCount: MAX_EVALUATION_POLL_FAILURES,
      fetchStatus: 'idle',
    });

    expect(await screen.findByText('확인 실패')).toBeVisible();
    expect(screen.getByRole('alert')).toHaveTextContent('평가 상태 확인을 중단했어요.');
    expect(screen.getByRole('button', { name: '✨ AI 평가 다시 요청' })).toBeEnabled();
    expect(screen.getByRole('link', { name: '이어 쓰기' })).toHaveAttribute(
      'aria-disabled',
      'false',
    );
    expect(
      screen.getByRole('button', { name: '문장 삭제: 상태 확인에 실패한 문장' }),
    ).toBeEnabled();
  });

  it('평가가 거부되면 팝업에서 수정하러 가기로 이동할 수 있다', async () => {
    const user = userEvent.setup();

    server.use(
      http.post('*/api/v1/sentence-versions/:versionId/evaluations', ({ params }) =>
        HttpResponse.json({
          sentenceVersionId: Number(params.versionId),
          evaluationStatus: 'REJECTED',
          safety: { status: 'REJECTED', reason: '유해 표현이 감지되었습니다.' },
          scores: null,
          wordBonus: null,
          gameBaseAttack: null,
          overallFeedback: null,
          moods: [],
          usedDailyWords: [],
          modelName: null,
          promptVersion: null,
          rubricVersion: null,
          evaluatedAt: '2026-07-31T02:00:00Z',
        }),
      ),
    );

    const queryClient = new QueryClient({
      defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
    });
    const router = createMemoryRouter(
      [
        { path: '/archive', element: <ArchivePage /> },
        { path: '/sentences/new', element: <div>문장 수정 화면</div> },
      ],
      { initialEntries: ['/archive?view=unevaluated'] },
    );

    render(
      <QueryClientProvider client={queryClient}>
        <ToastProvider>
          <RouterProvider router={router} />
        </ToastProvider>
      </QueryClientProvider>,
    );

    const evaluateButtons = await screen.findAllByRole('button', { name: '✨ AI 평가 요청' });
    await user.click(evaluateButtons[0]);

    // 거부 사유가 팝업으로 표시되고, 수정하러 가기로 문장 작성 화면에 진입한다.
    const dialog = await screen.findByRole('dialog');
    expect(dialog).toHaveTextContent('유해 표현이 감지되었습니다.');
    expect(dialog).toHaveTextContent('거부된 문장은 목록에 보관되지 않아요.');

    await user.click(screen.getByRole('button', { name: '수정하러 가기' }));

    expect(await screen.findByText('문장 수정 화면')).toBeVisible();
    expect(router.state.location.pathname).toBe('/sentences/new');
    expect(router.state.location.search).toBe('?versionId=92');
  });

  it('거부 팝업을 수정 없이 닫으면 거부된 문장을 삭제한다', async () => {
    const user = userEvent.setup();
    let deletedSentenceId: number | null = null;

    server.use(
      http.post('*/api/v1/sentence-versions/:versionId/evaluations', ({ params }) =>
        HttpResponse.json({
          sentenceVersionId: Number(params.versionId),
          evaluationStatus: 'REJECTED',
          safety: { status: 'REJECTED', reason: '유해 표현이 감지되었습니다.' },
          scores: null,
          wordBonus: null,
          gameBaseAttack: null,
          overallFeedback: null,
          moods: [],
          usedDailyWords: [],
          modelName: null,
          promptVersion: null,
          rubricVersion: null,
          evaluatedAt: '2026-07-31T02:00:00Z',
        }),
      ),
      http.delete('*/api/v1/sentences/:sentenceId', ({ params }) => {
        deletedSentenceId = Number(params.sentenceId);
        return new HttpResponse(null, { status: 204 });
      }),
    );

    renderArchivePage('/archive?view=unevaluated');

    const evaluateButtons = await screen.findAllByRole('button', { name: '✨ AI 평가 요청' });
    await user.click(evaluateButtons[0]);

    await screen.findByRole('dialog');
    await user.click(screen.getByRole('button', { name: '닫기' }));

    // 미평가 목록의 92번 버전은 sentenceId 47 문장의 유일한 버전이다.
    await waitFor(() => expect(deletedSentenceId).toBe(47));
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
  });

  it('참여한 원정 탭에서 이력과 공동 책 이동 링크를 표시한다', async () => {
    const user = userEvent.setup();
    const router = renderArchivePage();

    await screen.findByText('총 2개의 문장');
    await user.click(screen.getByRole('tab', { name: '참여한 원정' }));

    expect(router.state.location.search).toContain('tab=bossRuns');
    expect(await screen.findByText('새벽의 수호자')).toBeVisible();
    expect(screen.getByText('클리어')).toBeVisible();
    expect(screen.getByRole('link', { name: '📖 공동 책 보기' })).toHaveAttribute(
      'href',
      '/library/books/18',
    );

    // 미클리어 종료 회차는 공동 책 미생성 안내를 표시한다.
    expect(screen.getByText('잿빛 문지기')).toBeVisible();
    expect(screen.getByText('클리어하지 못해 공동 책이 만들어지지 않았어요.')).toBeVisible();

    const raidLinks = screen.getAllByRole('link', { name: '원정 보기' });
    expect(raidLinks[0]).toHaveAttribute('href', '/boss/7');
  });

  it('기여한 책 탭에서 내가 수록된 공동 책 목록을 표시한다', async () => {
    const user = userEvent.setup();
    const router = renderArchivePage();

    await screen.findByText('총 2개의 문장');
    await user.click(screen.getByRole('tab', { name: '기여한 책' }));

    expect(router.state.location.search).toContain('tab=books');
    expect(await screen.findByText('새벽의 수호자의 공동 책')).toBeVisible();
    expect(screen.getByText(/전체 12개 중 내 문장 2개 수록/)).toBeVisible();
    expect(screen.getByText('총 1권의 책')).toBeVisible();
    expect(screen.getByRole('link', { name: '책 보러 가기' })).toHaveAttribute(
      'href',
      '/library/books/18',
    );
  });

  it('개인 책 탭은 준비 중 안내를 표시한다', async () => {
    const user = userEvent.setup();
    renderArchivePage();

    await screen.findByText('총 2개의 문장');
    await user.click(screen.getByRole('tab', { name: '개인 책' }));

    expect(await screen.findByText('개인 책은 준비 중이에요')).toBeVisible();
  });
});
