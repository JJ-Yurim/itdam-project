export { AuthPageShell } from './components/AuthPageShell';
export { AuthSessionControl } from './components/AuthSessionControl';
export { AuthGateProvider, AuthRequiredLink } from './components/AuthGate';
export { useAuthGate } from './authGateContext';
export { LoginRequiredModal } from './components/LoginRequiredModal';
export { RequireAuthentication } from './components/RequireAuthentication';
export { LoginForm } from './components/LoginForm';
export { getSocialLoginErrorMessage } from './loginErrors';
export { ProfileAvatar } from './components/ProfileAvatar';
export { SignupForm } from './components/SignupForm';
export { SocialLoginButtons } from './components/SocialLoginButtons';
export { SocialSignupForm } from './components/SocialSignupForm';
export {
  useConfirmEmailVerificationMutation,
  useCompleteSocialRegistrationMutation,
  useLoginMutation,
  useLogoutMutation,
  useSendEmailVerificationMutation,
  useSignupMutation,
} from './mutations';
export {
  useCurrentCopyrightAgreementQuery,
  useCurrentUserQuery,
  useSocialRegistrationPendingQuery,
} from './queries';
export { authQueryKeys } from './queryKeys';

export type {
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
  UserRole,
  UserStatus,
} from './types';
