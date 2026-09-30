import { act, fireEvent, render, screen } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';

import { ToastProvider } from './ToastProvider';
import { useToast } from './useToast';

function ToastTrigger() {
  const { showToast } = useToast();

  return (
    <button type="button" onClick={() => showToast({ message: '저장했습니다.' })}>
      토스트 표시
    </button>
  );
}

function renderToastProvider() {
  return render(
    <ToastProvider>
      <ToastTrigger />
    </ToastProvider>,
  );
}

describe('ToastProvider', () => {
  afterEach(() => {
    vi.useRealTimers();
  });

  it('토스트를 3초 뒤에 닫는다', () => {
    vi.useFakeTimers();
    renderToastProvider();

    fireEvent.click(screen.getByRole('button', { name: '토스트 표시' }));
    expect(screen.getByRole('status')).toHaveTextContent('저장했습니다.');

    act(() => vi.advanceTimersByTime(3_000));

    expect(screen.queryByRole('status')).not.toBeInTheDocument();
  });

  it('언마운트할 때 남은 토스트 타이머를 정리한다', () => {
    vi.useFakeTimers();
    const { unmount } = renderToastProvider();

    fireEvent.click(screen.getByRole('button', { name: '토스트 표시' }));
    expect(vi.getTimerCount()).toBe(1);

    unmount();

    expect(vi.getTimerCount()).toBe(0);
  });
});
