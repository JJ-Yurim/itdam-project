import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { HttpResponse, http } from 'msw';
import { describe, expect, it } from 'vitest';

import { ToastProvider } from '@/shared/components';
import { server } from '@/mocks/server';
import { MemoryRouter } from 'react-router-dom';

import { BossManagementTab } from './BossManagementTab';

function renderTab(initialEntry = '/admin/raids?tab=bosses') {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  });

  render(
    <QueryClientProvider client={queryClient}>
      <ToastProvider>
        <MemoryRouter initialEntries={[initialEntry]}>
          <BossManagementTab />
        </MemoryRouter>
      </ToastProvider>
    </QueryClientProvider>,
  );
}

describe('BossManagementTab', () => {
  it('표에 표지·이름·상태·분위기·페이즈 수·수정 시각을 표시한다', async () => {
    renderTab();

    const table = await screen.findByRole('table', { name: '보스 목록' });
    const row = within(table).getByRole('row', { name: /새벽의 수호자/ });
    expect(within(row).getByRole('img', { name: /새벽의 수호자/ })).toBeInTheDocument();
    expect(within(row).getByText('활성')).toBeInTheDocument();
    expect(within(row).getByText(/평온\(주제\)/)).toBeInTheDocument();
    expect(within(row).getByText('1개')).toBeInTheDocument();
    // 목 보스의 updatedAt(2026-07-30T01:23:45Z)을 로컬 표기로 보여 준다.
    expect(row).toHaveTextContent(/26\. 7\. 30\./);
  });

  it('좁은 화면용 카드 목록에도 같은 보스를 담는다', async () => {
    renderTab();

    const cards = await screen.findByRole('list', { name: '보스 목록 카드' });
    expect(within(cards).getByText('새벽의 수호자')).toBeInTheDocument();
    expect(within(cards).getByText(/페이즈 1개/)).toBeInTheDocument();
  });

  it('검색어와 상태 필터를 서버 조회에 실어 보낸다', async () => {
    const user = userEvent.setup();
    const requests: string[] = [];
    server.use(
      http.get('*/api/admin/bosses', ({ request }) => {
        requests.push(new URL(request.url).search);
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
    await screen.findByText('등록된 보스가 없습니다');

    await user.type(screen.getByRole('searchbox', { name: '보스 이름 검색' }), '수호자');
    await user.click(screen.getByRole('button', { name: '검색' }));
    await waitFor(() =>
      expect(requests.at(-1)).toContain('query=%EC%88%98%ED%98%B8%EC%9E%90'),
    );

    await user.selectOptions(screen.getByRole('combobox', { name: '상태' }), 'INACTIVE');
    await waitFor(() => expect(requests.at(-1)).toContain('status=INACTIVE'));
  });

  it('새 보스 버튼으로 등록 패널을 연다', async () => {
    const user = userEvent.setup();
    renderTab();
    await screen.findByRole('table', { name: '보스 목록' });

    await user.click(screen.getByRole('button', { name: '새 보스' }));

    expect(screen.getByRole('dialog', { name: '새 보스 등록' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '보스 생성' })).toBeInTheDocument();
  });

  it('수정 버튼으로 기존 값이 채워진 수정 패널을 연다', async () => {
    const user = userEvent.setup();
    renderTab();
    // 표와 카드가 같은 동작 버튼을 함께 담고 있어(화면 폭으로만 한쪽을 감춘다) 표로 좁힌다.
    const table = await screen.findByRole('table', { name: '보스 목록' });

    await user.click(within(table).getByRole('button', { name: '새벽의 수호자 수정' }));

    const drawer = screen.getByRole('dialog', { name: '보스 수정' });
    expect(within(drawer).getByLabelText(/^이름/)).toHaveValue('새벽의 수호자');
  });

  it('비활성화는 결과를 알려 주는 확인을 거친 뒤에만 반영된다', async () => {
    const user = userEvent.setup();
    renderTab();
    const table = await screen.findByRole('table', { name: '보스 목록' });

    await user.click(within(table).getByRole('button', { name: '새벽의 수호자 비활성화' }));

    const dialog = screen.getByRole('dialog', { name: '보스 비활성화' });
    expect(within(dialog).getByText(/새 레이드 선택지에서 빠집니다/)).toBeInTheDocument();

    await user.click(within(dialog).getByRole('button', { name: '비활성화' }));

    await waitFor(() =>
      expect(
        within(screen.getByRole('table', { name: '보스 목록' })).getByRole('button', {
          name: '새벽의 수호자 활성화',
        }),
      ).toBeInTheDocument(),
    );
    expect(
      within(screen.getByRole('table', { name: '보스 목록' })).getByText('비활성'),
    ).toBeInTheDocument();
  });

  it('확인을 취소하면 상태를 바꾸지 않는다', async () => {
    const user = userEvent.setup();
    let statusCalled = false;
    server.use(
      http.patch('*/api/admin/bosses/:bossId/status', () => {
        statusCalled = true;
        return HttpResponse.json({});
      }),
    );

    renderTab();
    const table = await screen.findByRole('table', { name: '보스 목록' });

    await user.click(within(table).getByRole('button', { name: '새벽의 수호자 비활성화' }));
    await user.click(screen.getByRole('button', { name: '취소' }));

    expect(screen.queryByRole('dialog', { name: '보스 비활성화' })).not.toBeInTheDocument();
    expect(statusCalled).toBe(false);
  });

  it('목록을 불러오지 못하면 다시 시도를 제공한다', async () => {
    server.use(
      http.get('*/api/admin/bosses', () =>
        HttpResponse.json({ code: 'INTERNAL_ERROR' }, { status: 500 }),
      ),
    );

    renderTab();

    expect(await screen.findByRole('alert')).toHaveTextContent('보스 목록을 불러오지 못했습니다');
    expect(screen.getByRole('button', { name: '다시 시도' })).toBeInTheDocument();
  });
});
