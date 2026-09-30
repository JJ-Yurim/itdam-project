import { QueryClient } from '@tanstack/react-query';

import { isClientError } from '@/shared/api';

export const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      staleTime: 30_000,
      // 4xx는 같은 요청을 다시 보내도 같은 답이 온다. 재시도하면 없는 리소스를 여는
      // 동안 로딩 화면만 길어지고 서버도 쓸모없는 요청을 두 번 받는다.
      retry: (failureCount, error) => !isClientError(error) && failureCount < 1,
      refetchOnWindowFocus: false,
    },
    mutations: {
      retry: false,
    },
  },
});
