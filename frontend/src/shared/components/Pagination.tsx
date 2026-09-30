import { Button } from './Button';

interface PaginationProps {
  /** 현재 페이지 (0부터 시작) */
  page: number;
  totalPages: number;
  onPrev: () => void;
  onNext: () => void;
  /** 다음 페이지 존재 여부. 응답에 hasNext가 있으면 그것을 쓰고, 없으면 페이지 수로 계산한다. */
  hasNext?: boolean;
  /** 목록 갱신 중이라 이동을 잠글 때 */
  disabled?: boolean;
  ariaLabel: string;
  /** 바깥 여백 등 배치 클래스. 페이지가 1개면 nav 자체가 사라지므로 여백도 함께 사라진다. */
  className?: string;
}

/** 이전/다음 페이지 이동. 페이지 표기는 버튼 사이에 붙여 한 묶음으로 읽히게 한다. */
export function Pagination({
  page,
  totalPages,
  onPrev,
  onNext,
  hasNext,
  disabled = false,
  ariaLabel,
  className,
}: PaginationProps) {
  if (totalPages <= 1) {
    return null;
  }

  return (
    <nav
      aria-label={ariaLabel}
      className={['flex items-center justify-center gap-3', className].filter(Boolean).join(' ')}
    >
      <Button
        variant="secondary"
        className="text-sm"
        disabled={disabled || page === 0}
        onClick={onPrev}
      >
        이전
      </Button>

      <span data-numeric className="min-w-12 text-center text-sm text-stone-600">
        {page + 1} / {totalPages}
      </span>

      <Button
        variant="secondary"
        className="text-sm"
        disabled={disabled || (hasNext !== undefined ? !hasNext : page + 1 >= totalPages)}
        onClick={onNext}
      >
        다음
      </Button>
    </nav>
  );
}
