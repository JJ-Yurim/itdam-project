export const FOCUSABLE_SELECTOR =
  'a[href], button:not([disabled]), input:not([disabled]), select:not([disabled]), textarea:not([disabled]), [tabindex]:not([tabindex="-1"])';

export function focusableElementsWithin(container: HTMLElement | null): HTMLElement[] {
  return Array.from(container?.querySelectorAll<HTMLElement>(FOCUSABLE_SELECTOR) ?? []);
}

/**
 * 모달 안에서 Tab 순환을 닫는다. 초점이 마지막 요소에서 첫 요소로(Shift는 반대로) 돌아오게 해
 * 뒤에 있는 화면으로 초점이 새지 않도록 한다.
 */
export function trapTab(container: HTMLElement | null, event: React.KeyboardEvent): void {
  if (event.key !== 'Tab') return;
  const focusables = focusableElementsWithin(container);
  if (focusables.length === 0) return;

  const first = focusables[0];
  const last = focusables[focusables.length - 1];

  if (!event.shiftKey && document.activeElement === last) {
    event.preventDefault();
    first.focus();
  }
  if (event.shiftKey && document.activeElement === first) {
    event.preventDefault();
    last.focus();
  }
}
