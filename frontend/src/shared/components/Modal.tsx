import { useEffect, useId, useRef } from 'react';
import type { ReactNode } from 'react';

interface ModalProps {
  open: boolean;
  title: string;
  children: ReactNode;
  footer?: ReactNode;
  onClose: () => void;
}

export function Modal({ open, title, children, footer, onClose }: ModalProps) {
  const dialogRef = useRef<HTMLDialogElement>(null);
  // 모달이 두 개 이상 마운트돼도 제목 id가 겹치지 않도록 인스턴스별 id를 쓴다.
  const titleId = useId();

  useEffect(() => {
    const dialog = dialogRef.current;

    if (!dialog) {
      return;
    }

    if (open && !dialog.open) {
      dialog.showModal();
    }

    if (!open && dialog.open) {
      dialog.close();
    }
  }, [open]);

  return (
    <dialog
      ref={dialogRef}
      aria-labelledby={titleId}
      className="m-auto w-[calc(100%-2rem)] max-w-md rounded-2xl bg-white p-0 text-stone-900 shadow-xl backdrop:bg-black/40"
      onCancel={(event) => {
        event.preventDefault();
        onClose();
      }}
      onClick={(event) => {
        if (event.target === event.currentTarget) {
          onClose();
        }
      }}
    >
      <section className="p-6">
        <header className="flex items-start justify-between gap-4">
          <h2 id={titleId} className="text-xl font-bold">
            {title}
          </h2>
          <button
            type="button"
            aria-label="모달 닫기"
            title="닫기"
            className="-mt-2 -mr-2 flex size-11 shrink-0 items-center justify-center rounded-full text-2xl leading-none text-stone-500 transition-colors hover:bg-stone-100 hover:text-stone-700 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-brand"
            onClick={onClose}
          >
            ×
          </button>
        </header>

        <div className="mt-4">{children}</div>

        {footer && <footer className="mt-6 flex justify-end gap-2">{footer}</footer>}
      </section>
    </dialog>
  );
}
