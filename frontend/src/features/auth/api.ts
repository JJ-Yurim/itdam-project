import axios from 'axios';

import { clearCsrfToken, httpClient, refreshCsrfToken } from '@/shared/api';

import type {
  CopyrightAgreementResponse,
  EmailVerificationConfirmRequest,
  EmailVerificationConfirmResponse,
  EmailVerificationSendRequest,
  EmailVerificationSendResponse,
  LoginRequest,
  SocialRegistrationPendingResponse,
  SocialRegistrationRequest,
  SignupRequest,
  UserResponse,
} from './types';

export async function getSocialRegistrationPending(): Promise<SocialRegistrationPendingResponse> {
  const { data } = await httpClient.get<SocialRegistrationPendingResponse>(
    '/v1/auth/social-registration',
  );
  return data;
}

export async function completeSocialRegistration(
  request: SocialRegistrationRequest,
): Promise<UserResponse> {
  await refreshCsrfToken();
  const { data } = await httpClient.post<UserResponse>('/v1/auth/social-registration', request);
  await refreshCsrfToken();
  return data;
}

export async function getCurrentCopyrightAgreement(): Promise<CopyrightAgreementResponse> {
  const { data } = await httpClient.get<CopyrightAgreementResponse>(
    '/v1/copyright-agreements/current',
  );

  return data;
}

export async function sendEmailVerification(
  request: EmailVerificationSendRequest,
): Promise<EmailVerificationSendResponse> {
  await refreshCsrfToken();

  const { data } = await httpClient.post<EmailVerificationSendResponse>(
    '/v1/email-verifications',
    request,
  );

  return data;
}

export async function confirmEmailVerification(
  request: EmailVerificationConfirmRequest,
): Promise<EmailVerificationConfirmResponse> {
  await refreshCsrfToken();
  const { data } = await httpClient.post<EmailVerificationConfirmResponse>(
    '/v1/email-verifications/confirm',
    request,
  );

  return data;
}

export async function signup(request: SignupRequest): Promise<UserResponse> {
  await refreshCsrfToken();

  const { data } = await httpClient.post<UserResponse>('/v1/users', request);

  return data;
}

export async function login(request: LoginRequest): Promise<UserResponse> {
  await refreshCsrfToken();

  const { data } = await httpClient.post<UserResponse>('/v1/auth/sessions', request);

  await refreshCsrfToken();

  return data;
}

export async function getCurrentUser(): Promise<UserResponse | null> {
  try {
    const { data } = await httpClient.get<UserResponse>('/v1/users/me');

    return data;
  } catch (error) {
    if (axios.isAxiosError(error) && error.response?.status === 401) {
      return null;
    }

    throw error;
  }
}

export async function logout(): Promise<void> {
  await refreshCsrfToken();
  await httpClient.post('/v1/auth/sessions/current');
  clearCsrfToken();
}
