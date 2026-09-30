import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { HttpResponse, http } from 'msw';
import { MemoryRouter } from 'react-router-dom';
import { describe, expect, it } from 'vitest';

import { ToastProvider } from '@/shared/components';
import { server } from '@/mocks/server';

import { BookManagementTab } from './BookManagementTab';

function renderTab(initialEntry = '/admin/raids?tab=books') {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  });

  render(
    <QueryClientProvider client={queryClient}>
      <ToastProvider>
        <MemoryRouter initialEntries={[initialEntry]}>
          <BookManagementTab />
        </MemoryRouter>
      </ToastProvider>
    </QueryClientProvider>,
  );
}

describe('BookManagementTab', () => {
  it('표에 표지·책 ID·보스·생성 상태·공개 상태·완성일·콘텐츠 수를 표시한다', async () => {
    renderTab();
    const table = await screen.findByRole('table', { name: '책 목록' });
    const row = within(table).getByRole('row', { name: /새벽의 수호자/ });

    expect(within(row).getByRole('img', { name: /새벽의 수호자/ })).toBeInTheDocument();
    expect(row).toHaveTextContent('18');
    // 생성 상태와 공개 상태는 별도 레이블로 읽혀야 한다.
    expect(within(row).getByText('생성 완료')).toBeInTheDocument();
    expect(within(row).getByText('공개')).toBeInTheDocument();
    expect(row).toHaveTextContent('8편');
    expect(row).toHaveTextContent(/26\. 7\. 30\./);
  });

  it('생성 중 책은 공개 상태와 생성 상태를 구분해 보여 준다', async () => {
    renderTab();
    const table = await screen.findByRole('table', { name: '책 목록' });
    const row = within(table).getByRole('row', { name: /심연의 기록자/ });

    expect(within(row).getByText('생성 중')).toBeInTheDocument();
    expect(within(row).getByText('비공개')).toBeInTheDocument();
  });

  it('공개 상태 필터를 서버 조회에 실어 보낸다', async () => {
    const user = userEvent.setup();
    const searches: string[] = [];
    server.use(
      http.get('*/api/admin/books', ({ request }) => {
        searches.push(new URL(request.url).search);
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
    await screen.findByText('조건에 맞는 책이 없습니다');

    await user.selectOptions(screen.getByRole('combobox', { name: '공개 상태' }), 'HIDDEN');
    await waitFor(() => expect(searches.at(-1)).toContain('visibility=HIDDEN'));

    await user.selectOptions(screen.getByRole('combobox', { name: '생성 상태' }), 'COMPLETED');
    await waitFor(() => expect(searches.at(-1)).toContain('status=COMPLETED'));
  });

  it('완료된 책을 비공개로 전환하면 표의 공개 상태가 바뀐다', async () => {
    const user = userEvent.setup();
    renderTab();
    const table = await screen.findByRole('table', { name: '책 목록' });

    await user.click(within(table).getByRole('button', { name: '새벽의 수호자 책 관리' }));

    const drawer = screen.getByRole('dialog', { name: '책 관리' });
    await user.click(within(drawer).getByRole('button', { name: '비공개로 전환' }));

    const dialog = screen.getByRole('dialog', { name: '책 비공개 전환' });
    expect(within(dialog).getByText(/도서관과 피드에서 숨겨집니다/)).toBeInTheDocument();
    await user.click(within(dialog).getByRole('button', { name: '비공개로 전환' }));

    // 목록에는 원래 비공개인 다른 책도 있으므로 대상 행으로 좁혀 확인한다.
    await waitFor(() => {
      const table = screen.getByRole('table', { name: '책 목록' });
      const row = within(table).getByRole('row', { name: /새벽의 수호자/ });
      expect(within(row).getByText('비공개')).toBeInTheDocument();
    });
    // 패널도 응답을 이어받아야 다음 동작이 반대 방향으로 뒤집힌다.
    expect(await within(drawer).findByRole('button', { name: '공개로 전환' })).toBeInTheDocument();
  });

  it('표지를 교체한 뒤에도 공개 전환이 409 없이 이어진다', async () => {
    // 표지 교체도 서버의 updatedAt을 올린다. 응답을 이어받지 않으면 다음 전환이 자기 자신
    // 때문에 409가 되고, 패널을 닫았다 열기 전까지 계속 실패한다.
    const user = userEvent.setup();
    renderTab();
    const table = await screen.findByRole('table', { name: '책 목록' });
    await user.click(within(table).getByRole('button', { name: '새벽의 수호자 책 관리' }));

    const drawer = screen.getByRole('dialog', { name: '책 관리' });
    // 파일 입력과 버튼이 같은 접근성 이름을 쓰므로 selector·role로 가른다.
    await user.upload(
      within(drawer).getByLabelText('새벽의 수호자 책 표지 교체', { selector: 'input' }),
      new File(['cover'], 'cover.webp', { type: 'image/webp' }),
    );
    await user.click(
      within(drawer).getByRole('button', { name: '새벽의 수호자 책 표지 교체' }),
    );
    await waitFor(() => expect(screen.getByText('표지를 교체했습니다.')).toBeInTheDocument());

    await user.click(within(drawer).getByRole('button', { name: '비공개로 전환' }));
    await user.click(
      within(screen.getByRole('dialog', { name: '책 비공개 전환' })).getByRole('button', {
        name: '비공개로 전환',
      }),
    );

    expect(await within(drawer).findByRole('button', { name: '공개로 전환' })).toBeInTheDocument();
    expect(screen.queryByText(/다른 관리자가/)).not.toBeInTheDocument();
  });

  it('알 수 없는 status 값이 URL에 남아 있으면 무시한다', async () => {
    // 다른 탭이 남긴 값이나 직접 조작한 값이 그대로 서버로 새면 목록만 조용히 비어 보인다.
    const searches: string[] = [];
    server.use(
      http.get('*/api/admin/books', ({ request }) => {
        searches.push(new URL(request.url).search);
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

    renderTab('/admin/raids?tab=books&status=SCHEDULED');
    await screen.findByText('조건에 맞는 책이 없습니다');

    expect(searches[0]).not.toContain('status=');
  });

  it('생성 중 책은 공개 전환 버튼을 제공하지 않는다', async () => {
    const user = userEvent.setup();
    renderTab();
    const table = await screen.findByRole('table', { name: '책 목록' });

    await user.click(within(table).getByRole('button', { name: '심연의 기록자 책 관리' }));

    const drawer = screen.getByRole('dialog', { name: '책 관리' });
    expect(within(drawer).getByText(/생성이 끝난 책만 공개 상태를 바꿀 수 있습니다/)).toBeInTheDocument();
    expect(within(drawer).queryByRole('button', { name: /전환/ })).not.toBeInTheDocument();
  });

  it('목록을 불러오지 못하면 다시 시도를 제공한다', async () => {
    server.use(
      http.get('*/api/admin/books', () =>
        HttpResponse.json({ code: 'INTERNAL_ERROR' }, { status: 500 }),
      ),
    );
    renderTab();

    expect(await screen.findByRole('alert')).toHaveTextContent('책 목록을 불러오지 못했습니다');
    expect(screen.getByRole('button', { name: '다시 시도' })).toBeInTheDocument();
  });
});
