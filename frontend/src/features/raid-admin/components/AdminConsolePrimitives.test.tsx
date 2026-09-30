import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { useState } from 'react';
import { MemoryRouter, useNavigate, useSearchParams } from 'react-router-dom';
import { describe, expect, it, vi } from 'vitest';

import { AdminDataTable } from './AdminDataTable';
import { AdminDrawer } from './AdminDrawer';
import { AdminFilters } from './AdminFilters';
import { AdminTabs } from './AdminTabs';
import { ConfirmDialog } from './ConfirmDialog';
import { StatusBadge } from './StatusBadge';

function BackButton() {
  const navigate = useNavigate();
  return (
    <button type="button" onClick={() => void navigate(-1)}>
      뒤로
    </button>
  );
}

function SearchParamsProbe() {
  const [searchParams] = useSearchParams();
  return (
    <div data-testid="search-params">
      {Array.from(searchParams.entries())
        .map(([key, value]) => `${key}=${value}`)
        .join(' ')}
    </div>
  );
}

describe('AdminTabs', () => {
  const tabs = [
    { id: 'bosses', label: '보스 관리' },
    { id: 'runs', label: '레이드 관리' },
    { id: 'books', label: '책 표지 관리' },
  ];

  function renderTabs(initialEntry: string) {
    render(
      <MemoryRouter initialEntries={[initialEntry]}>
        <AdminTabs label="관리 메뉴" tabs={tabs} renderPanel={(tabId) => <div>{tabId} 패널</div>} />
        <SearchParamsProbe />
        <BackButton />
      </MemoryRouter>,
    );
  }

  it('URL tab 파라미터와 동기화되고 탭 전환 시 페이지 파라미터를 초기화한다', async () => {
    const user = userEvent.setup();
    renderTabs('/admin/raids?tab=runs&page=3');

    expect(screen.getByRole('tab', { name: '레이드 관리' })).toHaveAttribute(
      'aria-selected',
      'true',
    );
    expect(screen.getByRole('tabpanel')).toHaveTextContent('runs 패널');

    await user.click(screen.getByRole('tab', { name: '책 표지 관리' }));

    expect(screen.getByRole('tabpanel')).toHaveTextContent('books 패널');
    expect(screen.getByTestId('search-params')).toHaveTextContent('tab=books');
    expect(screen.getByTestId('search-params')).not.toHaveTextContent('page=');
  });

  it('탭을 옮기면 이전 탭의 검색 조건을 남기지 않는다', async () => {
    // 상태 필터처럼 이름이 같아도 뜻이 다른 조건이 남으면 다음 탭이 남의 조건을 읽는다.
    const user = userEvent.setup();
    renderTabs('/admin/raids?tab=bosses&query=수호자&status=ACTIVE&page=2');

    await user.click(screen.getByRole('tab', { name: '레이드 관리' }));

    expect(screen.getByTestId('search-params')).toHaveTextContent('tab=runs');
    expect(screen.getByTestId('search-params')).not.toHaveTextContent('query=');
    expect(screen.getByTestId('search-params')).not.toHaveTextContent('status=');
    expect(screen.getByTestId('search-params')).not.toHaveTextContent('page=');
  });

  it('알 수 없는 tab 값이면 첫 탭을 선택한다', () => {
    renderTabs('/admin/raids?tab=unknown');

    expect(screen.getByRole('tab', { name: '보스 관리' })).toHaveAttribute('aria-selected', 'true');
    expect(screen.getByRole('tabpanel')).toHaveTextContent('bosses 패널');
  });

  it('탭 목록이 비어 있으면 아무것도 렌더링하지 않는다', () => {
    // 권한이나 기능 여부로 탭을 계산하는 호출자가 생기면 빈 배열이 들어올 수 있다.
    render(
      <MemoryRouter initialEntries={['/admin/raids']}>
        <AdminTabs label="관리 메뉴" tabs={[]} renderPanel={() => <div>패널</div>} />
      </MemoryRouter>,
    );

    expect(screen.queryByRole('tablist')).not.toBeInTheDocument();
    expect(screen.queryByText('패널')).not.toBeInTheDocument();
  });

  it('탭 전환을 히스토리에 쌓아 뒤로가기로 이전 탭에 돌아온다', async () => {
    const user = userEvent.setup();
    renderTabs('/admin/raids');

    await user.click(screen.getByRole('tab', { name: '책 표지 관리' }));
    expect(screen.getByRole('tabpanel')).toHaveTextContent('books 패널');

    await user.click(screen.getByRole('button', { name: '뒤로' }));
    expect(screen.getByRole('tab', { name: '보스 관리' })).toHaveAttribute('aria-selected', 'true');
    expect(screen.getByRole('tabpanel')).toHaveTextContent('bosses 패널');
  });

  it('화살표·Home·End 키로 탭을 순환 이동하고 선택한다', async () => {
    const user = userEvent.setup();
    renderTabs('/admin/raids');

    screen.getByRole('tab', { name: '보스 관리' }).focus();
    await user.keyboard('{ArrowRight}');
    expect(screen.getByRole('tab', { name: '레이드 관리' })).toHaveFocus();
    expect(screen.getByRole('tab', { name: '레이드 관리' })).toHaveAttribute(
      'aria-selected',
      'true',
    );

    await user.keyboard('{End}');
    expect(screen.getByRole('tab', { name: '책 표지 관리' })).toHaveFocus();

    await user.keyboard('{ArrowRight}');
    expect(screen.getByRole('tab', { name: '보스 관리' })).toHaveFocus();

    await user.keyboard('{ArrowLeft}');
    expect(screen.getByRole('tab', { name: '책 표지 관리' })).toHaveFocus();

    await user.keyboard('{Home}');
    expect(screen.getByRole('tab', { name: '보스 관리' })).toHaveFocus();
  });
});

describe('AdminDrawer', () => {
  function DrawerFixture({ dirty = false }: { dirty?: boolean }) {
    const [open, setOpen] = useState(false);
    return (
      <div>
        <button type="button" onClick={() => setOpen(true)}>
          패널 열기
        </button>
        <AdminDrawer open={open} title="보스 수정" dirty={dirty} onClose={() => setOpen(false)}>
          <input aria-label="이름" />
        </AdminDrawer>
      </div>
    );
  }

  it('열리면 초점이 패널 안으로 이동하고 Escape로 닫으면 트리거로 복귀한다', async () => {
    const user = userEvent.setup();
    render(<DrawerFixture />);
    const trigger = screen.getByRole('button', { name: '패널 열기' });

    await user.click(trigger);
    const drawer = screen.getByRole('dialog', { name: '보스 수정' });
    expect(drawer.contains(document.activeElement)).toBe(true);

    await user.keyboard('{Escape}');
    expect(screen.queryByRole('dialog', { name: '보스 수정' })).not.toBeInTheDocument();
    expect(trigger).toHaveFocus();
  });

  it('수정 중이면 닫기 전에 폐기 확인을 거치고 취소하면 계속 작성한다', async () => {
    const user = userEvent.setup();
    render(<DrawerFixture dirty />);

    await user.click(screen.getByRole('button', { name: '패널 열기' }));
    await user.click(screen.getByRole('button', { name: '패널 닫기' }));

    expect(screen.getByText('작성 중인 내용이 사라집니다. 패널을 닫을까요?')).toBeInTheDocument();
    expect(screen.getByRole('dialog', { name: '보스 수정' })).toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: '계속 작성' }));
    expect(screen.getByRole('dialog', { name: '보스 수정' })).toBeInTheDocument();
    expect(
      screen.queryByText('작성 중인 내용이 사라집니다. 패널을 닫을까요?'),
    ).not.toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: '패널 닫기' }));
    await user.click(screen.getByRole('button', { name: '닫기' }));
    expect(screen.queryByRole('dialog', { name: '보스 수정' })).not.toBeInTheDocument();
  });

  it('데스크톱 폭 제한 클래스를 적용한다', async () => {
    // 좁은 화면 전체 폭은 기본값이고, 데스크톱에서만 560px로 좁히는 것이 회귀 위험이다.
    const user = userEvent.setup();
    render(<DrawerFixture />);

    await user.click(screen.getByRole('button', { name: '패널 열기' }));
    const drawer = screen.getByRole('dialog', { name: '보스 수정' });
    expect(drawer.className).toContain('md:max-w-[560px]');
  });

  it('폐기 확인이 열려 있으면 Tab 초점이 확인 대화상자 안에 머문다', async () => {
    // 확인 대화상자가 떠 있는데 뒤쪽 입력으로 초점이 새면 결정 전에 편집을 계속할 수 있다.
    const user = userEvent.setup();
    render(<DrawerFixture dirty />);

    await user.click(screen.getByRole('button', { name: '패널 열기' }));
    await user.click(screen.getByRole('button', { name: '패널 닫기' }));

    const confirmDialog = screen.getByRole('dialog', { name: '작성 중인 내용 폐기' });
    expect(confirmDialog.contains(document.activeElement)).toBe(true);

    await user.tab();
    expect(confirmDialog.contains(document.activeElement)).toBe(true);
    await user.tab();
    expect(confirmDialog.contains(document.activeElement)).toBe(true);
    await user.tab({ shift: true });
    expect(confirmDialog.contains(document.activeElement)).toBe(true);
  });
});

describe('ConfirmDialog', () => {
  it('제목·설명을 표시하고 확인과 취소 콜백을 호출한다', async () => {
    const onConfirm = vi.fn();
    const onCancel = vi.fn();
    const user = userEvent.setup();
    render(
      <ConfirmDialog
        open
        title="레이드 취소 확인"
        description="이 레이드가 취소 상태로 전환됩니다."
        confirmLabel="레이드 취소"
        onConfirm={onConfirm}
        onCancel={onCancel}
      />,
    );

    const dialog = screen.getByRole('dialog', { name: '레이드 취소 확인' });
    expect(within(dialog).getByText('이 레이드가 취소 상태로 전환됩니다.')).toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: '레이드 취소' }));
    expect(onConfirm).toHaveBeenCalledTimes(1);

    await user.click(screen.getByRole('button', { name: '취소' }));
    expect(onCancel).toHaveBeenCalledTimes(1);
  });
});

describe('AdminDataTable', () => {
  const rows = [{ id: 1, name: '새벽의 수호자', status: '진행 중' }];
  const columns = [
    { key: 'name', header: '이름', render: (row: (typeof rows)[number]) => row.name },
    { key: 'status', header: '상태', render: (row: (typeof rows)[number]) => row.status },
  ];

  function renderTable() {
    render(
      <AdminDataTable
        label="보스 목록"
        columns={columns}
        rows={rows}
        rowKey={(row) => row.id}
        renderCard={(row) => <span>{row.name} 카드</span>}
        emptyTitle="등록된 보스가 없습니다"
      />,
    );
  }

  it('시맨틱 표와 카드 목록에 같은 데이터를 렌더링한다', () => {
    renderTable();

    const table = screen.getByRole('table', { name: '보스 목록' });
    expect(within(table).getByRole('columnheader', { name: '이름' })).toBeInTheDocument();
    expect(within(table).getByRole('cell', { name: '새벽의 수호자' })).toBeInTheDocument();

    const cards = screen.getByRole('list', { name: '보스 목록 카드' });
    expect(within(cards).getByText('새벽의 수호자 카드')).toBeInTheDocument();
  });

  it('뷰포트 폭에 따라 표와 카드를 번갈아 노출하는 클래스를 적용한다', () => {
    renderTable();

    expect(screen.getByRole('table', { name: '보스 목록' }).parentElement?.className).toContain(
      'md:block',
    );
    expect(screen.getByRole('list', { name: '보스 목록 카드' }).className).toContain('md:hidden');
  });

  it('빈 목록이면 표 대신 빈 상태를 표시한다', () => {
    render(
      <AdminDataTable
        label="보스 목록"
        columns={columns}
        rows={[]}
        rowKey={(row) => row.id}
        renderCard={(row) => <span>{row.name}</span>}
        emptyTitle="등록된 보스가 없습니다"
      />,
    );

    expect(screen.getByText('등록된 보스가 없습니다')).toBeInTheDocument();
    expect(screen.queryByRole('table')).not.toBeInTheDocument();
  });
});

describe('AdminFilters', () => {
  it('검색과 상태 필터를 URL 검색 파라미터와 동기화하고 페이지를 초기화한다', async () => {
    const user = userEvent.setup();
    render(
      <MemoryRouter initialEntries={['/admin/raids?tab=runs&page=2']}>
        <AdminFilters
          searchLabel="보스 이름 검색"
          selects={[
            {
              key: 'status',
              label: '상태',
              emptyLabel: '전체 상태',
              options: [{ value: 'ACTIVE', label: '진행 중' }],
            },
          ]}
        />
        <SearchParamsProbe />
      </MemoryRouter>,
    );

    await user.type(screen.getByRole('searchbox', { name: '보스 이름 검색' }), '수호자');
    await user.click(screen.getByRole('button', { name: '검색' }));
    expect(screen.getByTestId('search-params')).toHaveTextContent('query=수호자');
    expect(screen.getByTestId('search-params')).toHaveTextContent('tab=runs');
    expect(screen.getByTestId('search-params')).not.toHaveTextContent('page=');

    await user.selectOptions(screen.getByRole('combobox', { name: '상태' }), 'ACTIVE');
    expect(screen.getByTestId('search-params')).toHaveTextContent('status=ACTIVE');

    await user.selectOptions(screen.getByRole('combobox', { name: '상태' }), '');
    expect(screen.getByTestId('search-params')).not.toHaveTextContent('status=');
  });
});

describe('StatusBadge', () => {
  it('라벨을 표시하고 톤 클래스를 적용한다', () => {
    render(<StatusBadge label="진행 중" tone="violet" />);
    const badge = screen.getByText('진행 중');
    expect(badge.className).toContain('bg-violet-50');
    expect(badge.className).toContain('text-violet-700');
  });
});
