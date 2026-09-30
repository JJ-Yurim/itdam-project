import type { ReactNode } from 'react';

import { EmptyState } from '@/shared/components';

export interface AdminTableColumn<Row> {
  key: string;
  header: string;
  render: (row: Row) => ReactNode;
  /** 숫자·시각처럼 오른쪽 정렬이 읽기 쉬운 열에 쓴다. */
  align?: 'left' | 'right';
}

interface AdminDataTableProps<Row> {
  label: string;
  columns: AdminTableColumn<Row>[];
  rows: Row[];
  rowKey: (row: Row) => string | number;
  /** 데스크톱 미만 폭에서 한 행을 카드로 그린다. */
  renderCard: (row: Row) => ReactNode;
  emptyTitle: string;
  emptyDescription?: string;
}

/**
 * 운영 목록. 데스크톱에서는 열을 비교할 수 있는 시맨틱 표로, 좁은 화면에서는 가로 스크롤 대신
 * 카드 목록으로 같은 데이터를 보여 준다.
 */
export function AdminDataTable<Row>({
  label,
  columns,
  rows,
  rowKey,
  renderCard,
  emptyTitle,
  emptyDescription,
}: AdminDataTableProps<Row>) {
  if (rows.length === 0) {
    return <EmptyState title={emptyTitle} description={emptyDescription} />;
  }

  return (
    <>
      <div className="hidden overflow-x-auto md:block">
        <table aria-label={label} className="w-full border-collapse text-sm">
          <thead>
            <tr className="border-b border-stone-200 text-left text-xs font-semibold text-stone-500">
              {columns.map((column) => (
                <th
                  key={column.key}
                  scope="col"
                  className={`px-3 py-2 ${column.align === 'right' ? 'text-right' : ''}`}
                >
                  {column.header}
                </th>
              ))}
            </tr>
          </thead>
          <tbody>
            {rows.map((row) => (
              <tr key={rowKey(row)} className="border-b border-stone-100 align-middle">
                {columns.map((column) => (
                  <td
                    key={column.key}
                    className={`px-3 py-3 ${column.align === 'right' ? 'text-right' : ''}`}
                  >
                    {column.render(row)}
                  </td>
                ))}
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      <ul aria-label={`${label} 카드`} className="space-y-3 md:hidden">
        {rows.map((row) => (
          <li key={rowKey(row)} className="rounded-xl border border-stone-200 p-4">
            {renderCard(row)}
          </li>
        ))}
      </ul>
    </>
  );
}
