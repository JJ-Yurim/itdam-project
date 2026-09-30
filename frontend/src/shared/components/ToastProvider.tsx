import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import type { PropsWithChildren } from 'react';

import { ToastContext, type ToastOptions, type ToastVariant } from './toastContext';

interface ToastItem {
  id: number;
  message: string;
  variant: ToastVariant;
}

const variantClasses: Record<ToastVariant, string> = {
  success: 'bg-emerald-600',
  error: 'bg-red-600',
  info: 'bg-stone-800',
};

let nextToastId = 0;

export function ToastProvider({ children }: PropsWithChildren) {
  const [toasts, setToasts] = useState<ToastItem[]>([]);
  const timeoutIdsRef = useRef<Set<number>>(new Set());

  useEffect(
    () => () => {
      timeoutIdsRef.current.forEach((timeoutId) => window.clearTimeout(timeoutId));
      timeoutIdsRef.current.clear();
    },
    [],
  );

  const showToast = useCallback(({ message, variant = 'info' }: ToastOptions) => {
    const id = nextToastId++;

    setToasts((current) => [...current, { id, message, variant }]);

    const timeoutId = window.setTimeout(() => {
      timeoutIdsRef.current.delete(timeoutId);
      setToasts((current) => current.filter((toast) => toast.id !== id));
    }, 3000);
    timeoutIdsRef.current.add(timeoutId);
  }, []);

  const contextValue = useMemo(() => ({ showToast }), [showToast]);

  return (
    <ToastContext.Provider value={contextValue}>
      {children}

      {/* 모바일은 하단 내비 위, 데스크톱은 화면 하단에 띄운다. */}
      <div
        aria-live="polite"
        className="fixed inset-x-4 bottom-[calc(env(safe-area-inset-bottom)+5.25rem)] z-50 flex flex-col items-center gap-2 lg:bottom-8"
      >
        {toasts.map(({ id, message, variant }) => (
          <div
            key={id}
            role={variant === 'error' ? 'alert' : 'status'}
            className={[
              'motion-rise w-full max-w-sm rounded-control px-4 py-3 text-sm font-medium text-white shadow-lg',
              variantClasses[variant],
            ].join(' ')}
          >
            {message}
          </div>
        ))}
      </div>
    </ToastContext.Provider>
  );
}
