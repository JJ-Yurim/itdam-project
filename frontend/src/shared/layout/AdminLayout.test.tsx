import { render, screen } from '@testing-library/react';
import { createMemoryRouter, RouterProvider } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { AdminLayout } from './AdminLayout';

const mocks = vi.hoisted(() => ({ currentUser: vi.fn() }));

vi.mock('@/features/auth', () => ({
  useCurrentUserQuery: mocks.currentUser,
  AuthSessionControl: () => <div>계정 메뉴</div>,
}));

function renderAdminLayout() {
  const router = createMemoryRouter(
    [
      {
        element: <AdminLayout />,
        children: [{ path: '/admin/raids', element: <div>관리 콘텐츠</div> }],
      },
      { path: '/', element: <div>홈 페이지</div> },
      { path: '/login', element: <div>로그인 페이지</div> },
    ],
    { initialEntries: ['/admin/raids'] },
  );

  render(<RouterProvider router={router} />);
}

describe('AdminLayout', () => {
  beforeEach(() => {
    mocks.currentUser.mockReturnValue({
      isPending: false,
      isError: false,
      data: { role: 'ADMIN', nickname: '운영자' },
    });
  });

  it('데스크톱 폭을 넓게 쓰고 앱 하단 네비게이션을 노출하지 않는다', () => {
    renderAdminLayout();

    const main = screen.getByRole('main');
    expect(main).toHaveClass('max-w-[1440px]');
    expect(main).toHaveTextContent('관리 콘텐츠');
    // 관리 콘솔은 모바일 앱 프레임을 쓰지 않는다.
    expect(screen.queryByRole('navigation', { name: '주요 메뉴' })).not.toBeInTheDocument();
    expect(main.className).not.toContain('max-w-[480px]');
  });

  it('서비스 복귀 링크와 계정 메뉴를 제공한다', () => {
    renderAdminLayout();

    expect(screen.getByRole('link', { name: '서비스로 돌아가기' })).toHaveAttribute('href', '/');
    expect(screen.getByText('계정 메뉴')).toBeInTheDocument();
    // 콘솔 이름은 브랜드 표기다. h1은 각 관리 화면이 가져 문서마다 하나만 있게 한다.
    expect(screen.getByText('잇담 운영 콘솔')).toBeInTheDocument();
    expect(screen.queryByRole('heading', { name: '잇담 운영 콘솔' })).not.toBeInTheDocument();
  });

  it('권한을 확인하는 동안 로딩 상태를 표시한다', () => {
    mocks.currentUser.mockReturnValue({ isPending: true, isError: false, data: undefined });
    renderAdminLayout();

    expect(screen.getByRole('status')).toHaveTextContent('관리자 권한을 확인하고 있습니다');
    expect(screen.queryByText('관리 콘텐츠')).not.toBeInTheDocument();
  });

  it('권한 확인이 실패하면 다시 시도를 제공한다', () => {
    mocks.currentUser.mockReturnValue({
      isPending: false,
      isError: true,
      data: undefined,
      refetch: vi.fn(),
    });
    renderAdminLayout();

    expect(screen.getByRole('alert')).toHaveTextContent('관리자 권한을 확인하지 못했습니다');
    expect(screen.getByRole('button', { name: '다시 시도' })).toBeInTheDocument();
  });

  it('비로그인 사용자를 로그인 페이지로 보낸다', () => {
    mocks.currentUser.mockReturnValue({ isPending: false, isError: false, data: null });
    renderAdminLayout();

    expect(screen.getByText('로그인 페이지')).toBeInTheDocument();
  });

  it('일반 사용자를 홈으로 보낸다', () => {
    mocks.currentUser.mockReturnValue({
      isPending: false,
      isError: false,
      data: { role: 'USER', nickname: '문장여행자' },
    });
    renderAdminLayout();

    expect(screen.getByText('홈 페이지')).toBeInTheDocument();
    expect(screen.queryByText('관리 콘텐츠')).not.toBeInTheDocument();
  });
});
