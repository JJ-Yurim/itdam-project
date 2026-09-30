import { useId } from 'react';
import { useSearchParams } from 'react-router-dom';

import { Button } from '@/shared/components';

export interface AdminFilterSelect {
  /** URL 검색 파라미터 이름 */
  key: string;
  label: string;
  /** 값을 비웠을 때 보여 줄 전체 선택지 문구 */
  emptyLabel: string;
  options: { value: string; label: string }[];
}

interface AdminFiltersProps {
  searchLabel: string;
  selects?: AdminFilterSelect[];
}

/**
 * 검색어와 상태 필터. 값을 URL에 두어 새로고침과 링크 공유가 같은 목록을 재현하게 한다.
 * 조건이 바뀌면 이전 페이지 번호가 남지 않도록 `page`를 지운다. 조건 변경은 히스토리를 늘리지
 * 않도록 현재 항목을 대체한다(뒤로가기는 탭 전환 단위로만 되돌아간다).
 */
export function AdminFilters({ searchLabel, selects = [] }: AdminFiltersProps) {
  const [searchParams, setSearchParams] = useSearchParams();
  const searchId = useId();
  const urlQuery = searchParams.get('query') ?? '';

  const applyParam = (key: string, value: string) => {
    const next = new URLSearchParams(searchParams);
    if (value) {
      next.set(key, value);
    } else {
      next.delete(key);
    }
    next.delete('page');
    setSearchParams(next, { replace: true });
  };

  return (
    <form
      className="flex flex-wrap items-end gap-3"
      onSubmit={(event) => {
        event.preventDefault();
        const submitted = new FormData(event.currentTarget).get('query');
        applyParam('query', String(submitted ?? '').trim());
      }}
    >
      <div className="flex min-w-0 flex-col gap-1">
        <label htmlFor={searchId} className="text-xs font-semibold text-stone-500">
          {searchLabel}
        </label>
        {/* 입력은 제출 시점에만 URL로 옮긴다. URL 값으로 리마운트시키면 입력창에서 Enter로
            제출할 때마다 노드가 교체되어 초점을 잃으므로, 첫 마운트 값만 URL에서 읽는다. */}
        <input
          id={searchId}
          name="query"
          type="search"
          defaultValue={urlQuery}
          className="min-h-11 w-56 rounded-lg border border-stone-300 px-3 text-sm focus-visible:outline-2 focus-visible:outline-offset-[-1px] focus-visible:outline-violet-600"
        />
      </div>

      {selects.map((select) => (
        <div key={select.key} className="flex flex-col gap-1">
          <label
            htmlFor={`${searchId}-${select.key}`}
            className="text-xs font-semibold text-stone-500"
          >
            {select.label}
          </label>
          <select
            id={`${searchId}-${select.key}`}
            value={searchParams.get(select.key) ?? ''}
            onChange={(event) => applyParam(select.key, event.target.value)}
            className="min-h-11 rounded-lg border border-stone-300 bg-white px-3 text-sm focus-visible:outline-2 focus-visible:outline-offset-[-1px] focus-visible:outline-violet-600"
          >
            <option value="">{select.emptyLabel}</option>
            {select.options.map((option) => (
              <option key={option.value} value={option.value}>
                {option.label}
              </option>
            ))}
          </select>
        </div>
      ))}

      <Button type="submit" variant="secondary">
        검색
      </Button>
    </form>
  );
}
