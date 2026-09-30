import { useSearchParams } from 'react-router-dom';

/**
 * 목록 페이지 번호를 URL에 둔다. 새로고침과 링크 공유가 같은 페이지를 재현하고, 탭이 바뀔 때
 * AdminTabs가 검색 파라미터를 비우므로 각 탭은 항상 첫 페이지에서 시작한다.
 * 페이지 이동은 히스토리를 늘리지 않도록 현재 항목을 대체한다.
 */
export function usePageParam(): [number, (next: number) => void] {
  const [searchParams, setSearchParams] = useSearchParams();
  const page = Math.max(Number(searchParams.get('page') ?? 0) || 0, 0);

  const changePage = (next: number) => {
    const params = new URLSearchParams(searchParams);
    if (next > 0) {
      params.set('page', String(next));
    } else {
      params.delete('page');
    }
    setSearchParams(params, { replace: true });
  };

  return [page, changePage];
}

/**
 * URL의 필터 값을 허용 목록으로 검증한다. 탭마다 status의 값 domain이 달라(보스 상태·레이드 상태·
 * 책 생성 상태) 다른 탭이 남긴 값이나 직접 조작한 값이 그대로 서버로 새면, 셀렉트에는 아무것도
 * 선택돼 있지 않은데 목록만 비어 보인다.
 */
export function readEnumParam<T extends string>(
  value: string | null,
  allowed: readonly T[],
): T | undefined {
  return allowed.find((candidate) => candidate === value);
}
