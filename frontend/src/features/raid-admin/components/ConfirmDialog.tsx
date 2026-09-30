import { useId, useRef } from 'react';

import { Button } from '@/shared/components';

import { trapTab } from './focusTrap';

interface ConfirmDialogProps {
  open: boolean;
  title: string;
  description: string;
  confirmLabel: string;
  cancelLabel?: string;
  /** 되돌리기 어려운 동작은 확인 버튼을 위험색으로 표시한다. */
  destructive?: boolean;
  onConfirm: () => void;
  onCancel: () => void;
}

/**
 * 상태 전환처럼 결과를 되돌리기 어려운 동작 앞에 세우는 확인 절차.
 * 드로어 위에 겹쳐 열리므로 native dialog 대신 role="dialog"로 직접 구성한다.
 */
export function ConfirmDialog({
  open,
  title,
  description,
  confirmLabel,
  cancelLabel = '취소',
  destructive = false,
  onConfirm,
  onCancel,
}: ConfirmDialogProps) {
  const titleId = useId();
  const descriptionId = useId();
  const dialogRef = useRef<HTMLElement>(null);

  if (!open) return null;

  return (
    <div className="fixed inset-0 z-60 flex items-center justify-center p-4">
      <div aria-hidden="true" className="absolute inset-0 bg-black/40" onClick={onCancel} />

      <section
        ref={dialogRef}
        role="dialog"
        aria-modal="true"
        aria-labelledby={titleId}
        aria-describedby={descriptionId}
        className="relative w-full max-w-md rounded-2xl bg-white p-6 shadow-xl"
        onKeyDown={(event) => {
          if (event.key === 'Escape') {
            event.stopPropagation();
            onCancel();
            return;
          }
          // 드로어 위에 겹쳐 열릴 수 있으므로 초점을 스스로 잡아 두고, 바깥 트랩까지
          // 이 키 입력이 올라가지 않게 막는다. 그러지 않으면 뒤쪽 패널로 초점이 샌다.
          if (event.key === 'Tab') {
            event.stopPropagation();
            trapTab(dialogRef.current, event);
          }
        }}
      >
        <h2 id={titleId} className="text-lg font-bold text-stone-900">
          {title}
        </h2>
        <p id={descriptionId} className="mt-2 text-sm text-stone-600">
          {description}
        </p>

        <footer className="mt-6 flex justify-end gap-2">
          <Button variant="secondary" onClick={onCancel}>
            {cancelLabel}
          </Button>
          {/* 확인 대화상자는 열리는 즉시 결정 버튼에 초점을 둬야 키보드로 바로 응답할 수 있다. */}
          <Button autoFocus variant={destructive ? 'danger' : 'primary'} onClick={onConfirm}>
            {confirmLabel}
          </Button>
        </footer>
      </section>
    </div>
  );
}
