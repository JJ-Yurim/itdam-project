import axios from 'axios';

import { getCsrfToken } from './csrfToken';

const STATE_CHANGING_METHODS = new Set(['POST', 'PUT', 'PATCH', 'DELETE']);

export const httpClient = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL,
  withCredentials: true,
  // 배열 파라미터를 `status=A&status=B`로 반복해 보낸다. axios 기본값은 `status[]=A`인데
  // Spring의 List 바인딩은 대괄호 없는 이름을 읽으므로 필터가 통째로 무시된다.
  paramsSerializer: { indexes: null },
});

httpClient.interceptors.request.use((config) => {
  const method = config.method?.toUpperCase() ?? '';
  const csrfToken = getCsrfToken();

  if (STATE_CHANGING_METHODS.has(method) && csrfToken) {
    config.headers.set('X-XSRF-TOKEN', csrfToken);
  }

  return config;
});
