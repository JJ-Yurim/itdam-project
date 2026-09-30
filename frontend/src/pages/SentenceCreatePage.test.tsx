import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { HttpResponse, http } from 'msw';
import { createMemoryRouter, RouterProvider } from 'react-router-dom';
import { beforeAll, describe, expect, it } from 'vitest';

import { server } from '@/mocks/server';

import { SentenceCreatePage } from './SentenceCreatePage';

beforeAll(() => {
  HTMLDialogElement.prototype.showModal = function showModal() {
    this.open = true;
  };
  HTMLDialogElement.prototype.close = function close() {
    this.open = false;
  };
});

describe('SentenceCreatePage', () => {
  it('오늘의 단어 기반 문장 생성 화면을 표시하고 개봉 안내 배너는 열지 않는다', async () => {
    const user = userEvent.setup();
    const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    const router = createMemoryRouter(
      [{ path: '/sentences/new', element: <SentenceCreatePage /> }],
      { initialEntries: ['/sentences/new'] },
    );

    render(
      <QueryClientProvider client={queryClient}>
        <RouterProvider router={router} />
      </QueryClientProvider>,
    );

    expect(await screen.findByRole('heading', { name: '오늘의 문장 만들기' })).toBeInTheDocument();
    expect(screen.getByRole('region', { name: '오늘의 단어팩' })).toBeInTheDocument();
    expect(
      screen.getByText(/동사·형용사는 시제나 형태가 바뀌면 사용 표시가 되지 않을 수 있어요/),
    ).toBeVisible();
    const textarea = screen.getByRole('textbox', { name: '작성할 문장' });
    expect(textarea).toHaveAttribute('maxlength', '500');
    expect(textarea).toHaveAttribute('aria-describedby', 'sentence-content-help');

    await user.click(textarea);

    const revealPromptText =
      '오늘의 단어를 최소 2개 개봉해서 사용해야 AI 평가를 받을 수 있어요.';
    expect(
      screen.queryByText((_, element) => element?.textContent === revealPromptText),
    ).not.toBeInTheDocument();
    expect(screen.queryByRole('note')).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: '그냥 쓸게요' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: '카드 개봉하기' })).not.toBeInTheDocument();
  });

  it('자동 저장 없이 서고에 저장 버튼으로만 DRAFT를 만든다', async () => {
    const user = userEvent.setup();
    let createCount = 0;

    server.use(
      http.post('*/api/v1/sentences', async ({ request }) => {
        createCount += 1;
        const body = (await request.json()) as { content: string };

        return HttpResponse.json(
          {
            id: 84,
            sentenceVersionId: 84,
            sentenceId: 42,
            versionNo: 1,
            content: body.content,
            evaluationStatus: 'DRAFT',
            createdAt: '2026-07-30T01:00:00Z',
            confirmedAt: null,
          },
          { status: 201 },
        );
      }),
    );

    const queryClient = new QueryClient({
      defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
    });
    const router = createMemoryRouter(
      [{ path: '/sentences/new', element: <SentenceCreatePage /> }],
      { initialEntries: ['/sentences/new'] },
    );

    render(
      <QueryClientProvider client={queryClient}>
        <RouterProvider router={router} />
      </QueryClientProvider>,
    );

    const textarea = await screen.findByRole('textbox', { name: '작성할 문장' });
    await user.type(textarea, '은하수 아래 윤슬이 반짝였다');

    // 입력을 멈춰도 자동 저장이 발생하지 않는다.
    await new Promise((resolve) => setTimeout(resolve, 1600));
    expect(createCount).toBe(0);
    expect(screen.getByText('저장하지 않은 변경이 있어요.')).toBeInTheDocument();

    // 오늘의 단어를 2개 사용했으므로 저장 전에도 평가 버튼이 활성화된다.
    expect(screen.getByRole('button', { name: '✨ 문장 분석 및 AI 평가 요청' })).toBeEnabled();

    await user.click(screen.getByRole('button', { name: '📚 미평가 문장 저장' }));

    expect(await screen.findByText('서고에 저장 완료')).toBeInTheDocument();
    expect(createCount).toBe(1);
    expect(router.state.location.search).toBe('?versionId=84');

    // 저장이 끝나면 미평가 문장 저장 버튼은 비활성화된다.
    expect(screen.getByRole('button', { name: '📚 미평가 문장 저장' })).toBeDisabled();
  });

  it('서고에 저장이 실패하면 실패 안내와 다시 저장 버튼을 표시한다', async () => {
    const user = userEvent.setup();

    server.use(
      http.post('*/api/v1/sentences', () =>
        HttpResponse.json({ code: 'INTERNAL_ERROR', message: '저장 실패' }, { status: 500 }),
      ),
    );

    const queryClient = new QueryClient({
      defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
    });
    const router = createMemoryRouter(
      [{ path: '/sentences/new', element: <SentenceCreatePage /> }],
      { initialEntries: ['/sentences/new'] },
    );

    render(
      <QueryClientProvider client={queryClient}>
        <RouterProvider router={router} />
      </QueryClientProvider>,
    );

    const textarea = await screen.findByRole('textbox', { name: '작성할 문장' });
    await user.type(textarea, '저장할 문장');
    await user.click(screen.getByRole('button', { name: '📚 미평가 문장 저장' }));

    // 실패 상태가 미저장 안내(waiting)에 가려지지 않고 그대로 표시된다.
    expect(await screen.findByText('서고에 저장하지 못했어요.')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '다시 저장' })).toBeVisible();
    expect(textarea).toHaveValue('저장할 문장');
  });

  // 사용 단어 수는 백엔드에서도 차단 조건이 아니라 보너스에만 반영되므로 평가를 막지 않는다.
  it('사용한 오늘의 단어가 적어도 평가를 요청할 수 있다', async () => {
    server.use(
      http.get('*/api/v1/sentence-versions/84', () =>
        HttpResponse.json({
          id: 84,
          sentenceVersionId: 84,
          sentenceId: 42,
          versionNo: 1,
          content: '은하수만 사용한 문장',
          evaluationStatus: 'DRAFT',
          createdAt: '2026-07-30T01:00:00Z',
          confirmedAt: null,
        }),
      ),
    );
    const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    const router = createMemoryRouter(
      [{ path: '/sentences/new', element: <SentenceCreatePage /> }],
      { initialEntries: ['/sentences/new?versionId=84'] },
    );

    render(
      <QueryClientProvider client={queryClient}>
        <RouterProvider router={router} />
      </QueryClientProvider>,
    );

    expect(
      await screen.findByRole('button', { name: '✨ 문장 분석 및 AI 평가 요청' }),
    ).toBeEnabled();
    expect(
      screen.queryByText('오늘의 단어를 2개 이상 사용하면 평가를 요청할 수 있어요.'),
    ).not.toBeInTheDocument();
  });

  it('저장된 DRAFT의 AI 평가를 요청하고 결과를 표시한다', async () => {
    const user = userEvent.setup();
    const queryClient = new QueryClient({
      defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
    });
    const router = createMemoryRouter(
      [{ path: '/sentences/new', element: <SentenceCreatePage /> }],
      { initialEntries: ['/sentences/new?versionId=84'] },
    );

    render(
      <QueryClientProvider client={queryClient}>
        <RouterProvider router={router} />
      </QueryClientProvider>,
    );

    const evaluationButton = await screen.findByRole('button', {
      name: '✨ 문장 분석 및 AI 평가 요청',
    });
    expect(evaluationButton).toBeEnabled();
    await user.click(evaluationButton);

    // 결과는 팝업 없이 아래에 남고, 어느 문장의 결과인지 함께 보여준다.
    expect(await screen.findByRole('heading', { name: '✨ AI 문장 평가 결과' })).toBeVisible();
    expect(screen.getByText('83.35')).toBeInTheDocument();
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();

    // 평가를 보낸 버전에서 에디터가 떨어져 나와 다음 문장을 곧바로 쓸 수 있다.
    const textbox = screen.getByRole('textbox', { name: '작성할 문장' });
    await waitFor(() => {
      expect(textbox).toBeEnabled();
      expect(textbox).toHaveValue('');
    });
    expect(router.state.location.search).toBe('');
  });

  it('평가를 기다리는 동안 쓰기 시작한 다음 문장은 결과가 와도 지우지 않는다', async () => {
    const user = userEvent.setup();
    // 응답이 즉시 오면 타이핑 전에 에디터가 비워져 정작 겹치는 구간을 못 지난다.
    let releaseEvaluation = () => {};
    const evaluationGate = new Promise<void>((resolve) => {
      releaseEvaluation = resolve;
    });
    server.use(
      http.post('*/api/v1/sentence-versions/:versionId/evaluations', async ({ params }) => {
        await evaluationGate;
        return HttpResponse.json({
          sentenceVersionId: Number(params.versionId),
          evaluationStatus: 'EVALUATED',
          totalScore: 84.2,
          completeness: 80,
          originality: 70,
          expressiveness: 75,
          imagery: 60,
          emotion: 50,
          wordBonus: 0,
          baseAttack: 84,
          usedWords: [],
          feedback: '좋은 문장이에요.',
        });
      }),
    );
    const queryClient = new QueryClient({
      defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
    });
    const router = createMemoryRouter(
      [{ path: '/sentences/new', element: <SentenceCreatePage /> }],
      { initialEntries: ['/sentences/new?versionId=84'] },
    );

    render(
      <QueryClientProvider client={queryClient}>
        <RouterProvider router={router} />
      </QueryClientProvider>,
    );

    const textbox = await screen.findByRole('textbox', { name: '작성할 문장' });
    await user.click(screen.getByRole('button', { name: '✨ 문장 분석 및 AI 평가 요청' }));

    expect(screen.getByRole('heading', { name: 'AI가 문장의 힘을 읽고 있어요' })).toBeVisible();
    expect(screen.getByRole('progressbar', { name: 'AI 문장 평가 진행 중' })).toBeVisible();

    // 아직 응답이 오지 않은 상태에서 다음 문장을 쓰기 시작한다.
    await user.clear(textbox);
    await user.type(textbox, '기다리는 동안 쓴 다음 문장');
    expect(textbox).toHaveValue('기다리는 동안 쓴 다음 문장');

    releaseEvaluation();
    expect(await screen.findByRole('heading', { name: '✨ AI 문장 평가 결과' })).toBeVisible();

    // 결과가 도착해도 쓰던 입력은 남고, 에디터는 평가한 버전에서 떨어져 나온다.
    await waitFor(() => expect(router.state.location.search).toBe(''));
    expect(textbox).toBeEnabled();
    expect(textbox).toHaveValue('기다리는 동안 쓴 다음 문장');
  });

  it('평가 서버 오류 시 횟수 미차감 안내와 재시도 버튼을 유지한다', async () => {
    const user = userEvent.setup();
    server.use(
      http.post('*/api/v1/sentence-versions/:versionId/evaluations', () =>
        HttpResponse.json(
          { code: 'INTERNAL_ERROR', message: 'AI 평가 실패', fieldErrors: [] },
          { status: 500 },
        ),
      ),
    );
    const queryClient = new QueryClient({
      defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
    });
    const router = createMemoryRouter(
      [{ path: '/sentences/new', element: <SentenceCreatePage /> }],
      { initialEntries: ['/sentences/new?versionId=84'] },
    );

    render(
      <QueryClientProvider client={queryClient}>
        <RouterProvider router={router} />
      </QueryClientProvider>,
    );

    const evaluationButton = await screen.findByRole('button', {
      name: '✨ 문장 분석 및 AI 평가 요청',
    });
    await user.click(evaluationButton);

    expect(await screen.findByRole('alert')).toHaveTextContent('평가 횟수는 차감되지 않으며');
    expect(evaluationButton).toBeEnabled();
  });

  it('거부된 문장을 수정하지 않고 떠나면 문장을 삭제한다', async () => {
    let deletedSentenceId: number | null = null;

    server.use(
      http.get('*/api/v1/sentence-versions/90', () =>
        HttpResponse.json({
          id: 90,
          sentenceVersionId: 90,
          sentenceId: 42,
          versionNo: 1,
          content: '거부된 문장',
          evaluationStatus: 'REJECTED',
          createdAt: '2026-07-30T01:00:00Z',
          confirmedAt: '2026-07-30T02:00:00Z',
        }),
      ),
      http.get('*/api/v1/sentence-versions/90/evaluation', () =>
        HttpResponse.json({
          sentenceVersionId: 90,
          evaluationStatus: 'REJECTED',
          safety: { status: 'REJECTED', reason: null },
          scores: null,
          wordBonus: null,
          gameBaseAttack: null,
          overallFeedback: null,
          moods: [],
          usedDailyWords: [],
          modelName: null,
          promptVersion: null,
          rubricVersion: null,
          evaluatedAt: '2026-07-30T02:00:00Z',
        }),
      ),
      http.delete('*/api/v1/sentences/:sentenceId', ({ params }) => {
        deletedSentenceId = Number(params.sentenceId);
        return new HttpResponse(null, { status: 204 });
      }),
    );
    const queryClient = new QueryClient({
      defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
    });
    const router = createMemoryRouter(
      [{ path: '/sentences/new', element: <SentenceCreatePage /> }],
      { initialEntries: ['/sentences/new?versionId=90'] },
    );

    const { unmount } = render(
      <QueryClientProvider client={queryClient}>
        <RouterProvider router={router} />
      </QueryClientProvider>,
    );

    await screen.findByText(
      '일부 표현이 서비스 이용 기준에 맞지 않아 평가를 진행하지 못했어요. 문장을 수정한 뒤 다시 시도해 주세요.',
    );

    unmount();

    await waitFor(() => expect(deletedSentenceId).toBe(42));
  });

  it('이전 버전이 있는 거부 문장은 떠나도 삭제하지 않는다', async () => {
    let deleteCalled = false;

    server.use(
      http.get('*/api/v1/sentence-versions/90', () =>
        HttpResponse.json({
          id: 90,
          sentenceVersionId: 90,
          sentenceId: 42,
          versionNo: 2,
          content: '거부된 문장',
          evaluationStatus: 'REJECTED',
          createdAt: '2026-07-30T01:00:00Z',
          confirmedAt: '2026-07-30T02:00:00Z',
        }),
      ),
      http.get('*/api/v1/sentence-versions/90/evaluation', () =>
        HttpResponse.json({
          sentenceVersionId: 90,
          evaluationStatus: 'REJECTED',
          safety: { status: 'REJECTED', reason: null },
          scores: null,
          wordBonus: null,
          gameBaseAttack: null,
          overallFeedback: null,
          moods: [],
          usedDailyWords: [],
          modelName: null,
          promptVersion: null,
          rubricVersion: null,
          evaluatedAt: '2026-07-30T02:00:00Z',
        }),
      ),
      http.delete('*/api/v1/sentences/:sentenceId', () => {
        deleteCalled = true;
        return new HttpResponse(null, { status: 204 });
      }),
    );
    const queryClient = new QueryClient({
      defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
    });
    const router = createMemoryRouter(
      [{ path: '/sentences/new', element: <SentenceCreatePage /> }],
      { initialEntries: ['/sentences/new?versionId=90'] },
    );

    const { unmount } = render(
      <QueryClientProvider client={queryClient}>
        <RouterProvider router={router} />
      </QueryClientProvider>,
    );

    await screen.findByText(
      '일부 표현이 서비스 이용 기준에 맞지 않아 평가를 진행하지 못했어요. 문장을 수정한 뒤 다시 시도해 주세요.',
    );

    unmount();

    // 삭제 예약은 setTimeout(0) 매크로태스크이므로, 그 뒤에 줄 세운
    // 타이머가 끝난 시점이면 예약이 있었다면 이미 발화했어야 한다.
    await new Promise((resolve) => setTimeout(resolve, 0));
    expect(deleteCalled).toBe(false);
  });

  it('거부 문장 화면을 떠났다가 곧바로 다시 열면 삭제하지 않는다', async () => {
    let deleteCalled = false;

    server.use(
      http.get('*/api/v1/sentence-versions/90', () =>
        HttpResponse.json({
          id: 90,
          sentenceVersionId: 90,
          sentenceId: 42,
          versionNo: 1,
          content: '거부된 문장',
          evaluationStatus: 'REJECTED',
          createdAt: '2026-07-30T01:00:00Z',
          confirmedAt: '2026-07-30T02:00:00Z',
        }),
      ),
      http.get('*/api/v1/sentence-versions/90/evaluation', () =>
        HttpResponse.json({
          sentenceVersionId: 90,
          evaluationStatus: 'REJECTED',
          safety: { status: 'REJECTED', reason: null },
          scores: null,
          wordBonus: null,
          gameBaseAttack: null,
          overallFeedback: null,
          moods: [],
          usedDailyWords: [],
          modelName: null,
          promptVersion: null,
          rubricVersion: null,
          evaluatedAt: '2026-07-30T02:00:00Z',
        }),
      ),
      http.delete('*/api/v1/sentences/:sentenceId', () => {
        deleteCalled = true;
        return new HttpResponse(null, { status: 204 });
      }),
    );

    // 뒤로 가기 재진입처럼 캐시가 남아 있는 상태를 재현하기 위해 QueryClient를 공유한다.
    const queryClient = new QueryClient({
      defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
    });
    const createRouter = () =>
      createMemoryRouter([{ path: '/sentences/new', element: <SentenceCreatePage /> }], {
        initialEntries: ['/sentences/new?versionId=90'],
      });

    const first = render(
      <QueryClientProvider client={queryClient}>
        <RouterProvider router={createRouter()} />
      </QueryClientProvider>,
    );

    await screen.findByText(
      '일부 표현이 서비스 이용 기준에 맞지 않아 평가를 진행하지 못했어요. 문장을 수정한 뒤 다시 시도해 주세요.',
    );

    first.unmount();

    render(
      <QueryClientProvider client={queryClient}>
        <RouterProvider router={createRouter()} />
      </QueryClientProvider>,
    );

    await screen.findByText(
      '일부 표현이 서비스 이용 기준에 맞지 않아 평가를 진행하지 못했어요. 문장을 수정한 뒤 다시 시도해 주세요.',
    );

    await new Promise((resolve) => setTimeout(resolve, 0));
    expect(deleteCalled).toBe(false);
  });

  it('거부된 문장을 수정해 새 DRAFT 버전으로 저장하고 재평가한다', async () => {
    const user = userEvent.setup();
    let createdVersionContent = '';
    server.use(
      http.get('*/api/v1/sentence-versions/90', () =>
        HttpResponse.json({
          id: 90,
          sentenceVersionId: 90,
          sentenceId: 42,
          versionNo: 1,
          content: '거부된 문장',
          evaluationStatus: 'REJECTED',
          createdAt: '2026-07-30T01:00:00Z',
          confirmedAt: '2026-07-30T02:00:00Z',
        }),
      ),
      http.get('*/api/v1/sentence-versions/90/evaluation', () =>
        HttpResponse.json({
          sentenceVersionId: 90,
          evaluationStatus: 'REJECTED',
          safety: { status: 'REJECTED', reason: null },
          scores: null,
          wordBonus: null,
          gameBaseAttack: null,
          overallFeedback: null,
          moods: [],
          usedDailyWords: [],
          modelName: null,
          promptVersion: null,
          rubricVersion: null,
          evaluatedAt: '2026-07-30T02:00:00Z',
        }),
      ),
      http.post('*/api/v1/sentences/42/versions', async ({ request }) => {
        const body = (await request.json()) as { content: string };
        createdVersionContent = body.content;
        return HttpResponse.json(
          {
            id: 91,
            sentenceVersionId: 91,
            sentenceId: 42,
            versionNo: 2,
            content: body.content,
            evaluationStatus: 'DRAFT',
            createdAt: '2026-07-30T03:00:00Z',
            confirmedAt: null,
          },
          { status: 201 },
        );
      }),
    );
    const queryClient = new QueryClient({
      defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
    });
    const router = createMemoryRouter(
      [{ path: '/sentences/new', element: <SentenceCreatePage /> }],
      { initialEntries: ['/sentences/new?versionId=90'] },
    );

    render(
      <QueryClientProvider client={queryClient}>
        <RouterProvider router={router} />
      </QueryClientProvider>,
    );

    expect(
      await screen.findByText(
        '일부 표현이 서비스 이용 기준에 맞지 않아 평가를 진행하지 못했어요. 문장을 수정한 뒤 다시 시도해 주세요.',
      ),
    ).toBeVisible();
    const textarea = screen.getByRole('textbox', { name: '작성할 문장' });
    expect(textarea).toBeDisabled();

    await user.click(screen.getByRole('button', { name: '이 문장 고쳐 쓰기' }));
    expect(textarea).toBeEnabled();
    await user.clear(textarea);

    await user.type(textarea, '은하수와 윤슬을 담아 수정한 문장');

    const reevaluationButton = screen.getByRole('button', {
      name: '✨ 문장 분석 및 AI 평가 요청',
    });
    await waitFor(() => expect(reevaluationButton).toBeEnabled());
    await user.click(reevaluationButton);

    // 버튼 한 번으로 새 버전 저장과 재평가가 이어진다.
    await waitFor(() => {
      expect(createdVersionContent).toBe('은하수와 윤슬을 담아 수정한 문장');
    });

    // 결과는 팝업 없이 아래에 남고, 에디터는 평가한 버전에서 떨어져 나온다.
    expect(await screen.findByRole('heading', { name: '✨ AI 문장 평가 결과' })).toBeVisible();
    expect(screen.queryByRole('heading', { name: 'AI 평가가 거부됐어요' })).not.toBeInTheDocument();
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
    await waitFor(() => {
      expect(textarea).toBeEnabled();
      expect(router.state.location.search).toBe('');
    });
  });
});
