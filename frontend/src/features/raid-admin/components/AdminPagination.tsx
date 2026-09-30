import { Button } from '@/shared/components';

interface AdminPaginationProps {
  page: number;
  totalPages: number;
  hasNext: boolean;
  label: string;
  onChange: (page: number) => void;
}

export function AdminPagination({
  page,
  totalPages,
  hasNext,
  label,
  onChange,
}: AdminPaginationProps) {
  if (totalPages <= 1) return null;

  return (
    <nav aria-label={`${label} 페이지`} className="mt-4 flex items-center justify-between">
      <Button variant="secondary" disabled={page === 0} onClick={() => onChange(page - 1)}>
        이전
      </Button>
      <span className="text-sm text-stone-600">
        {page + 1} / {totalPages}
      </span>
      <Button variant="secondary" disabled={!hasNext} onClick={() => onChange(page + 1)}>
        다음
      </Button>
    </nav>
  );
}
