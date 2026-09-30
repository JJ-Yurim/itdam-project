import { createContext, useContext } from 'react';

export interface AuthGateValue {
  isUnauthenticated: boolean;
  isAuthenticationReady: boolean;
  requestLogin: (returnTo?: string) => void;
}

export const AuthGateContext = createContext<AuthGateValue | null>(null);

export function useAuthGate() {
  const value = useContext(AuthGateContext);
  if (!value) {
    throw new Error('useAuthGate must be used within AuthGateProvider');
  }
  return value;
}
