import type { PropsWithChildren } from 'react';

import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { act, renderHook } from '@testing-library/react';
import { HttpResponse, http } from 'msw';
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom';
import { describe, expect, it } from 'vitest';

import { dailyWordKeys } from '@/features/daily-words';
import { server } from '@/mocks/server';

import { useLoginMutation, useLogoutMutation } from './mutations';
import { authQueryKeys } from './queryKeys';

function harness(initialPath = '/') {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  });
  // 로그아웃 훅이 홈으로 보내므로 라우터가 필요하고, 이동 결과를 단정할 수 있게
  // 현재 경로를 기록해 둔다.
  const location = { pathname: initialPath };
  function LocationProbe({ children }: PropsWithChildren) {
    location.pathname = useLocation().pathname;
    return children;
  }
  const wrapper = ({ children }: PropsWithChildren) => (
    <QueryClientProvider client={queryClient}>
      <MemoryRouter initialEntries={[initialPath]}>
        <Routes>
          <Route path="*" element={<LocationProbe>{children}</LocationProbe>} />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>
  );

  return { queryClient, wrapper, location };
}

/** 이전 사용자가 남긴 캐시. 키에 사용자 식별자가 없어 그대로 재사용될 수 있다. */
function seedPreviousUserCache(queryClient: QueryClient) {
  queryClient.setQueryData(dailyWordKeys.today(), { items: [{ wordId: 1, word: '이전사용자단어' }] });
}

describe('인증 경계의 캐시 초기화', () => {
  it('로그인하면 이전 사용자의 캐시를 남기지 않는다', async () => {
    // 계정을 바꿨는데 이전 사용자의 오늘의 단어가 그대로 보이던 문제.
    // staleTime이 5분이라 그 안에 전환하면 재요청 없이 옛 캐시가 쓰였다.
    server.use(
      http.post('*/api/v1/auth/sessions', () =>
        HttpResponse.json({ userId: 2, email: 'next@example.com', nickname: 'next', role: 'USER' }),
      ),
    );
    const { queryClient, wrapper } = harness();
    seedPreviousUserCache(queryClient);

    const { result } = renderHook(() => useLoginMutation(), { wrapper });
    await act(async () => {
      await result.current.mutateAsync({ email: 'next@example.com', password: 'password123' });
    });

    expect(queryClient.getQueryData(dailyWordKeys.today())).toBeUndefined();
    // 방금 확정된 세션은 남아야 한다. clear 뒤에 심지 않으면 함께 지워진다.
    expect(queryClient.getQueryData(authQueryKeys.currentUser())).toMatchObject({
      nickname: 'next',
    });
  });

  it('로그아웃하면 그 사용자의 캐시를 남기지 않는다', async () => {
    server.use(
      http.post('*/api/v1/auth/sessions/current', () => new HttpResponse(null, { status: 204 })),
    );
    const { queryClient, wrapper, location } = harness('/archive');
    seedPreviousUserCache(queryClient);

    const { result } = renderHook(() => useLogoutMutation(), { wrapper });
    await act(async () => {
      await result.current.mutateAsync();
    });

    expect(queryClient.getQueryData(dailyWordKeys.today())).toBeUndefined();
    expect(queryClient.getQueryData(authQueryKeys.currentUser())).toBeNull();
    // 회원 전용 화면에 남겨 두면 방금 로그아웃한 사람이 곧바로 로그인 안내 모달을 만난다.
    expect(location.pathname).toBe('/');
  });
});
