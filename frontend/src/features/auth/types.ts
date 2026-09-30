export interface SignupRequest {
  email: string;
  password: string;
  nickname: string;
  verificationToken: string;
  copyrightAgreementId: number;
  copyrightAgreed: true;
}

export interface CopyrightAgreementResponse {
  id: number;
  version: string;
  title: string;
  content: string;
  effectiveAt: string;
  validMonths: number;
}

export interface EmailVerificationSendRequest {
  email: string;
}

export interface EmailVerificationSendResponse {
  expiresInSeconds: number;
  resendCooldownSeconds: number;
}

export interface EmailVerificationConfirmRequest {
  email: string;
  code: string;
}

export interface EmailVerificationConfirmResponse {
  verificationToken: string;
  validForSeconds: number;
}

export interface LoginRequest {
  email: string;
  password: string;
}

export interface SocialRegistrationPendingResponse {
  pending: boolean;
}

export interface SocialRegistrationRequest {
  nickname: string;
  copyrightAgreementId: number;
  copyrightAgreed: true;
}

export type UserStatus = 'ACTIVE' | 'INACTIVE';
export type UserRole = 'USER' | 'ADMIN';

export interface UserResponse {
  id: number;
  email: string;
  nickname: string;
  status: UserStatus;
  role: UserRole;
  createdAt: string;
}
