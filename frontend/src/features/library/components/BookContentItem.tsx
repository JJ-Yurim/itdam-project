import type { BookContentResponse, BookContentType } from '../types';

interface BookContentItemProps {
  content: BookContentResponse;
  isMine?: boolean;
}

const contentTypeLabels: Record<BookContentType, string> = {
  USER_ORIGINAL: '사용자 원문',
  USER_EDITED: '사용자 편집본',
  AI_GENERATED: 'AI 생성문',
};

const contentTypeClasses: Record<BookContentType, string> = {
  USER_ORIGINAL: 'bg-emerald-50 text-emerald-700',
  USER_EDITED: 'bg-blue-50 text-blue-700',
  AI_GENERATED: 'bg-violet-50 text-violet-700',
};

export function BookContentItem({ content, isMine = false }: BookContentItemProps) {
  const contributorName = content.contributor?.nickname ?? 'AI';

  return (
    <article
      className={[
        'rounded-2xl border bg-white p-4 shadow-sm',
        isMine ? 'border-violet-300 ring-2 ring-violet-100' : 'border-stone-200',
      ].join(' ')}
    >
      <div className="flex flex-wrap items-center gap-2">
        <span className="rounded-full bg-stone-100 px-3 py-1 text-xs font-bold text-stone-600">
          #{content.sequenceNo}
        </span>

        <span
          className={[
            'rounded-full px-3 py-1 text-xs font-bold',
            contentTypeClasses[content.contentType],
          ].join(' ')}
        >
          {contentTypeLabels[content.contentType]}
        </span>

        {isMine && (
          <span className="rounded-full bg-violet-600 px-3 py-1 text-xs font-bold text-white">
            내 문장
          </span>
        )}
      </div>

      <p className="mt-4 whitespace-pre-wrap text-sm leading-7 text-stone-800">{content.content}</p>

      <dl className="mt-4 grid gap-2 text-sm">
        <div className="flex items-center justify-between gap-3 rounded-xl bg-stone-50 px-3 py-2">
          <dt className="text-stone-500">기여자</dt>
          <dd className="font-bold text-stone-900">{contributorName}</dd>
        </div>

        {content.sourceSubmissionId && (
          <div className="flex items-center justify-between gap-3 rounded-xl bg-stone-50 px-3 py-2">
            <dt className="text-stone-500">제출 ID</dt>
            <dd className="font-bold text-stone-900">{content.sourceSubmissionId}</dd>
          </div>
        )}
      </dl>

      {content.contentType === 'USER_EDITED' && (
        <p className="mt-3 rounded-xl bg-amber-50 px-3 py-2 text-xs leading-5 text-amber-800">
          원문 비교는 백엔드에서 원문 필드가 제공되면 연결할 예정입니다.
        </p>
      )}
    </article>
  );
}
