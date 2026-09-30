import type { ReactNode } from 'react';

import { Button, ButtonLink } from './Button';
import { Spinner } from './Spinner';

interface StateProps {
  title: string;
  description?: string;
}

interface ErrorStateProps extends StateProps {
  onRetry?: () => void;
  /**
   * 대상이 없거나 접근할 수 없을 때 돌아갈 곳.
   *
   * 404·403에서는 다시 시도해도 같은 결과라, 재시도 버튼만 두면 사용자가 그 화면에
   * 갇힌다. **이 값을 넘기면 onRetry는 렌더하지 않는다.** 둘 다 필요한 상황이 아직
   * 없어 단순하게 두었으니, 호출부는 backTo를 줄 때 onRetry를 넘기지 않는 편이 좋다.
   */
  backTo?: { to: string; label: string };
}

export function LoadingState({
  title = '불러오는 중입니다',
  description = '잠시만 기다려 주세요.',
}: Partial<StateProps>) {
  return (
    <section role="status" aria-live="polite" className="py-12 text-center">
      <Spinner className="mx-auto size-6 text-brand" />
      <p className="mt-3 font-semibold text-stone-900">{title}</p>
      <p className="mt-2 text-sm text-stone-500">{description}</p>
    </section>
  );
}

export function ErrorState({ title, description, onRetry, backTo }: ErrorStateProps) {
  return (
    <section role="alert" className="py-12 text-center">
      <p className="font-semibold text-red-700">{title}</p>
      {description && <p className="mt-2 text-sm text-stone-500">{description}</p>}
      {backTo ? (
        <ButtonLink to={backTo.to} className="mt-4">
          {backTo.label}
        </ButtonLink>
      ) : (
        onRetry && (
          <Button className="mt-4" onClick={onRetry}>
            다시 시도
          </Button>
        )
      )}
    </section>
  );
}

interface EmptyStateProps extends StateProps {
  /** 상황을 표현하는 선화 아이콘. 이모지 대신 SVG를 쓴다. */
  icon?: ReactNode;
  /** 사용자가 이 상태에서 벗어날 수 있는 다음 행동 (보통 ButtonLink). */
  action?: ReactNode;
}

export function EmptyState({ title, description, icon, action }: EmptyStateProps) {
  return (
    <section className="py-12 text-center">
      {icon && (
        <span
          aria-hidden="true"
          className="mx-auto mb-4 flex size-12 items-center justify-center rounded-full bg-stone-100 text-stone-400"
        >
          {icon}
        </span>
      )}
      <p className="font-semibold text-stone-700">{title}</p>
      {description && <p className="mt-2 text-sm text-stone-500">{description}</p>}
      {action && <div className="mt-5 flex justify-center">{action}</div>}
    </section>
  );
}
