import { Button } from '@/shared/components';

import type { CopyrightAgreementResponse } from '../types';

interface CopyrightAgreementFieldProps {
  agreement?: CopyrightAgreementResponse;
  isPending: boolean;
  isError: boolean;
  isFetching: boolean;
  checked: boolean;
  onCheckedChange: (checked: boolean) => void;
  onRetry: () => void;
}

export function CopyrightAgreementField({
  agreement,
  isPending,
  isError,
  isFetching,
  checked,
  onCheckedChange,
  onRetry,
}: CopyrightAgreementFieldProps) {
  return (
    <section
      className="rounded-xl border border-stone-200 bg-stone-50 p-4"
      aria-labelledby="copyright-agreement-title"
    >
      <div className="mb-3 flex items-center justify-between gap-3">
        <h3 id="copyright-agreement-title" className="font-semibold text-stone-800">
          저작권 이용 동의
        </h3>
        {agreement && <span className="text-xs text-stone-500">버전 {agreement.version}</span>}
      </div>

      {isPending && (
        <p className="text-sm text-stone-600" role="status">
          동의서를 불러오는 중...
        </p>
      )}

      {isError && (
        <div className="space-y-3">
          <p className="text-sm text-red-700" role="alert">
            저작권 이용 동의서를 불러오지 못했습니다.
          </p>
          <Button type="button" variant="secondary" disabled={isFetching} onClick={onRetry}>
            {isFetching ? '다시 불러오는 중...' : '동의서 다시 불러오기'}
          </Button>
        </div>
      )}

      {agreement && !isError && (
        <div className="space-y-3">
          <div className="max-h-40 overflow-y-auto rounded-lg border border-stone-200 bg-white p-3">
            <p className="mb-2 text-sm font-semibold text-stone-800">{agreement.title}</p>
            <p className="whitespace-pre-wrap text-sm leading-6 text-stone-600">
              {agreement.content}
            </p>
          </div>
          <label className="flex cursor-pointer items-start gap-3 text-sm text-stone-700">
            <input
              type="checkbox"
              className="mt-0.5 size-4 accent-violet-600"
              checked={checked}
              onChange={(event) => onCheckedChange(event.target.checked)}
            />
            <span>[필수] 위 저작권 이용 동의서 내용을 확인했으며 이에 동의합니다.</span>
          </label>
        </div>
      )}
    </section>
  );
}
