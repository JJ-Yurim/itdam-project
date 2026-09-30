import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { RaidAdminPage } from './RaidAdminPage';

const mocks = vi.hoisted(() => ({ currentUser: vi.fn() }));

vi.mock('@/features/auth', () => ({ useCurrentUserQuery: mocks.currentUser }));
// 각 탭의 표·필터·패널 동작은 탭별 테스트가 다룬다. 여기서는 접근 제어와 탭 분리만 본다.
vi.mock('@/features/raid-admin/components/BossManagementTab', () => ({
  BossManagementTab: () => <div>보스 관리 탭</div>,
}));
vi.mock('@/features/raid-admin/components/RaidManagementTab', () => ({
  RaidManagementTab: () => <div>레이드 관리 탭</div>,
}));
vi.mock('@/features/raid-admin/components/BookManagementTab', () => ({
  BookManagementTab: () => <div>책 관리 탭</div>,
}));

function renderPage(initialEntry = '/admin/raids') {
  render(
    <MemoryRouter initialEntries={[initialEntry]}>
      <Routes>
        <Route path="/admin/raids" element={<RaidAdminPage />} />
        <Route path="/login" element={<div>로그인 페이지</div>} />
        <Route path="/" element={<div>홈 페이지</div>} />
      </Routes>
    </MemoryRouter>,
  );
}

describe('RaidAdminPage 접근 제어', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('권한을 확인하는 동안 로딩 상태를 표시한다', () => {
    mocks.currentUser.mockReturnValue({ isPending: true, data: undefined });
    renderPage();
    expect(screen.getByRole('status')).toHaveTextContent('관리자 권한을 확인하고 있습니다');
  });

  it('비로그인 사용자를 로그인 페이지로 이동시킨다', async () => {
    mocks.currentUser.mockReturnValue({ isPending: false, data: null });
    renderPage();
    expect(await screen.findByText('로그인 페이지')).toBeInTheDocument();
    expect(screen.queryByText('보스 관리 탭')).not.toBeInTheDocument();
  });

  it('일반 사용자를 홈으로 이동시킨다', async () => {
    mocks.currentUser.mockReturnValue({ isPending: false, data: { role: 'USER' } });
    renderPage();
    expect(await screen.findByText('홈 페이지')).toBeInTheDocument();
    expect(screen.queryByText('보스 관리 탭')).not.toBeInTheDocument();
  });

  it('관리자에게 관리자 페이지를 표시한다', () => {
    mocks.currentUser.mockReturnValue({ isPending: false, data: { role: 'ADMIN' } });
    renderPage();
    expect(screen.getByRole('heading', { name: '레이드 관리' })).toBeInTheDocument();
    expect(screen.getByText('보스 관리 탭')).toBeInTheDocument();
  });

  it('보스·레이드·책을 각각의 탭으로 나눠 보여준다', async () => {
    const user = userEvent.setup();
    mocks.currentUser.mockReturnValue({ isPending: false, data: { role: 'ADMIN' } });
    renderPage();

    expect(screen.getByRole('tab', { name: '보스 관리' })).toHaveAttribute('aria-selected', 'true');
    expect(screen.getByText('보스 관리 탭')).toBeInTheDocument();

    await user.click(screen.getByRole('tab', { name: '레이드 관리' }));
    expect(screen.getByText('레이드 관리 탭')).toBeInTheDocument();
    expect(screen.queryByText('보스 관리 탭')).not.toBeInTheDocument();

    await user.click(screen.getByRole('tab', { name: '책 관리' }));
    expect(screen.getByText('책 관리 탭')).toBeInTheDocument();
    expect(screen.queryByText('레이드 관리 탭')).not.toBeInTheDocument();
  });

  it('URL tab 파라미터로 진입한 탭을 그대로 복원한다', () => {
    mocks.currentUser.mockReturnValue({ isPending: false, data: { role: 'ADMIN' } });
    renderPage('/admin/raids?tab=books');

    expect(screen.getByRole('tab', { name: '책 관리' })).toHaveAttribute('aria-selected', 'true');
    expect(screen.getByText('책 관리 탭')).toBeInTheDocument();
  });
});
