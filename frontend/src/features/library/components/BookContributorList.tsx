import { useState } from 'react';

import { Pagination } from '@/shared/components';

import type { BookContributorResponse } from '../types';

interface BookContributorListProps {
  contributors: BookContributorResponse[];
}

const CONTRIBUTORS_PER_PAGE = 6;

export function BookContributorList({ contributors }: BookContributorListProps) {
  const [page, setPage] = useState(0);

  if (contributors.length === 0) {
    return <p className="mt-3 text-sm text-stone-500">표시할 기여 작가 정보가 없습니다.</p>;
  }

  const totalPages = Math.ceil(contributors.length / CONTRIBUTORS_PER_PAGE);
  const currentPage = Math.min(page, totalPages - 1);
  const startIndex = currentPage * CONTRIBUTORS_PER_PAGE;
  const visibleContributors = contributors.slice(startIndex, startIndex + CONTRIBUTORS_PER_PAGE);
  const endIndex = startIndex + visibleContributors.length;

  return (
    <div className="mt-3">
      {totalPages > 1 && (
        <p role="status" className="mb-2 text-xs font-semibold text-stone-500">
          전체 {contributors.length}명 중 {startIndex + 1}–{endIndex}명
        </p>
      )}

      <ul
        aria-label="기여 작가 전체 목록"
        className="grid gap-2 sm:grid-cols-2 lg:grid-cols-1 xl:grid-cols-2"
      >
        {visibleContributors.map((contributor) => (
          <li
            key={contributor.userId === null ? `nickname:${contributor.nickname}` : contributor.userId}
            className="flex min-w-0 items-center gap-2.5 rounded-xl bg-violet-50 px-3 py-2.5"
          >
            <span
              aria-hidden="true"
              className="flex size-9 shrink-0 items-center justify-center rounded-full bg-white text-sm font-extrabold text-violet-700 shadow-sm"
            >
              {contributor.nickname.slice(0, 1)}
            </span>
            <span className="min-w-0 break-words text-sm font-bold text-stone-800">
              {contributor.nickname}
            </span>
          </li>
        ))}
      </ul>

      <Pagination
        page={currentPage}
        totalPages={totalPages}
        onPrev={() => setPage((current) => Math.max(0, current - 1))}
        onNext={() => setPage((current) => Math.min(totalPages - 1, current + 1))}
        ariaLabel="기여 작가 페이지 이동"
        className="mt-4"
      />
    </div>
  );
}
