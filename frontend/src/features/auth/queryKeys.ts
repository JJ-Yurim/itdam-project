export const authQueryKeys = {
  all: ['auth'] as const,
  currentUser: () => [...authQueryKeys.all, 'current-user'] as const,
  currentCopyrightAgreement: () =>
    [...authQueryKeys.all, 'copyright-agreement', 'current'] as const,
  socialRegistrationPending: () =>
    [...authQueryKeys.all, 'social-registration', 'pending'] as const,
};
