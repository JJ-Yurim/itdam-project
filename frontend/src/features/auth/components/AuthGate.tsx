import { useCallback, useMemo, useState } from 'react';
import type { MouseEvent, ReactNode } from 'react';
import { createPath, Link, useLocation } from 'react-router-dom';
import type { LinkProps } from 'react-router-dom';

import { LoginRequiredModal } from './LoginRequiredModal';
import { useCurrentUserQuery } from '../queries';
import { AuthGateContext, useAuthGate } from '../authGateContext';

export function AuthGateProvider({
  children,
}: {
  children: ReactNode;
}) {
  const location = useLocation();
  const currentUserQuery = useCurrentUserQuery();
  const isUnauthenticated = currentUserQuery.isSuccess && currentUserQuery.data === null;
  const [returnTo, setReturnTo] = useState<string | null>(null);
  const requestLogin = useCallback(
    (destination?: string) => setReturnTo(destination ?? location.pathname),
    [location.pathname],
  );
  const value = useMemo(
    () => ({
      isUnauthenticated,
      isAuthenticationReady: currentUserQuery.isSuccess,
      requestLogin,
    }),
    [currentUserQuery.isSuccess, isUnauthenticated, requestLogin],
  );

  return (
    <AuthGateContext.Provider value={value}>
      {children}
      <LoginRequiredModal
        open={returnTo !== null}
        returnTo={returnTo ?? '/'}
        onClose={() => setReturnTo(null)}
      />
    </AuthGateContext.Provider>
  );
}

export function AuthRequiredLink({ onClick, to, ...props }: LinkProps) {
  const { isUnauthenticated, isAuthenticationReady, requestLogin } = useAuthGate();

  const handleClick = (event: MouseEvent<HTMLAnchorElement>) => {
    onClick?.(event);
    if (event.defaultPrevented) return;

    if (!isAuthenticationReady) {
      event.preventDefault();
      return;
    }

    if (!isUnauthenticated) return;

    event.preventDefault();
    requestLogin(typeof to === 'string' ? to : createPath(to));
  };

  return <Link {...props} to={to} onClick={handleClick} />;
}
