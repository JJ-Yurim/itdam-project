import { useEffect, useId, useRef, useState } from 'react';
import type { ReactNode } from 'react';

import { ConfirmDialog } from './ConfirmDialog';
import { focusableElementsWithin, trapTab } from './focusTrap';

interface AdminDrawerProps {
  open: boolean;
  title: string;
  /** 작성 중인 내용이 있으면 닫기 전에 폐기 확인을 거친다. */
  dirty?: boolean;
  children: ReactNode;
  footer?: ReactNode;
  onClose: () => void;
}

/**
 * 생성·수정을 처리하는 우측 패널. 데스크톱에서는 목록을 곁눈으로 보며 편집할 수 있도록
 * 560px로 제한하고, 좁은 화면에서는 입력 폭을 확보하기 위해 전체 화면으로 전환한다.
 */
export function AdminDrawer({ open, ...props }: AdminDrawerProps) {
  // 패널이 닫힐 때 초점 복귀와 상태 정리를 확실히 하려고 열려 있는 동안만 마운트한다.
  if (!open) return null;
  return <DrawerPanel {...props} />;
}

function DrawerPanel({
  title,
  dirty = false,
  children,
  footer,
  onClose,
}: Omit<AdminDrawerProps, 'open'>) {
  const titleId = useId();
  const panelRef = useRef<HTMLElement>(null);
  const [isDiscardConfirmOpen, setIsDiscardConfirmOpen] = useState(false);

  useEffect(() => {
    const previouslyFocused = document.activeElement as HTMLElement | null;
    // 닫기 버튼보다 첫 입력에 초점을 두면 바로 작성에 들어갈 수 있다.
    const target = focusableElementsWithin(panelRef.current).find(
      (element) => element.dataset.drawerClose !== 'true',
    );
    (target ?? panelRef.current)?.focus();

    return () => previouslyFocused?.focus();
  }, []);

  const attemptClose = () => {
    if (dirty) {
      setIsDiscardConfirmOpen(true);
      return;
    }
    onClose();
  };

  return (
    <div className="fixed inset-0 z-50 flex justify-end">
      <div aria-hidden="true" className="absolute inset-0 bg-black/40" onClick={attemptClose} />

      <section
        ref={panelRef}
        role="dialog"
        aria-modal="true"
        aria-labelledby={titleId}
        tabIndex={-1}
        className="relative flex h-full w-full flex-col bg-white shadow-xl md:max-w-[560px]"
        onKeyDown={(event) => {
          // 폐기 확인이 떠 있으면 초점과 Escape는 확인 대화상자가 가진다.
          if (isDiscardConfirmOpen) return;
          if (event.key === 'Escape') {
            event.stopPropagation();
            attemptClose();
            return;
          }
          trapTab(panelRef.current, event);
        }}
      >
        <header className="flex items-start justify-between gap-4 border-b border-stone-200 px-5 py-4">
          <h2 id={titleId} className="text-lg font-bold text-stone-900">
            {title}
          </h2>
          <button
            type="button"
            data-drawer-close="true"
            aria-label="패널 닫기"
            className="rounded-lg px-2 text-2xl leading-none text-stone-500 transition hover:bg-stone-100 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-violet-600"
            onClick={attemptClose}
          >
            ×
          </button>
        </header>

        <div className="min-h-0 flex-1 overflow-y-auto px-5 py-4">{children}</div>

        {footer && (
          <footer className="flex justify-end gap-2 border-t border-stone-200 px-5 py-4">
            {footer}
          </footer>
        )}

        <ConfirmDialog
          open={isDiscardConfirmOpen}
          title="작성 중인 내용 폐기"
          description="작성 중인 내용이 사라집니다. 패널을 닫을까요?"
          confirmLabel="닫기"
          cancelLabel="계속 작성"
          destructive
          onConfirm={() => {
            setIsDiscardConfirmOpen(false);
            onClose();
          }}
          onCancel={() => setIsDiscardConfirmOpen(false)}
        />
      </section>
    </div>
  );
}
