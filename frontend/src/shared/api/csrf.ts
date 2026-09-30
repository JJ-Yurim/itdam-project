import { setCsrfToken } from './csrfToken';
import { httpClient } from './httpClient';

interface CsrfTokenResponse {
  token: string;
}

export async function refreshCsrfToken() {
  const { data } = await httpClient.get<CsrfTokenResponse>('/v1/csrf');

  setCsrfToken(data.token);

  return data.token;
}
