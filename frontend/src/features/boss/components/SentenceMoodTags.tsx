import { getMoodName, useArchiveDetailQuery } from '@/features/archive';

interface SentenceMoodTagsProps {
  sentenceVersionId: number;
}

/**
 * 문장의 분위기 해시태그. 제출 가능 문장 API에는 분위기가 없어
 * 내 서고 상세를 조회해 표시한다. 조회 실패 시 아무것도 렌더링하지 않는다.
 */
export function SentenceMoodTags({ sentenceVersionId }: SentenceMoodTagsProps) {
  const detailQuery = useArchiveDetailQuery(sentenceVersionId);
  const moods = detailQuery.data?.moods ?? [];

  if (moods.length === 0) {
    return null;
  }

  return (
    <span className="mt-2 flex flex-wrap gap-1.5">
      {moods.map((mood) => (
        <span
          key={mood.code}
          className="max-w-full break-keep rounded bg-stone-100 px-2 py-0.5 text-[11px] text-stone-600 lg:px-3 lg:py-1 lg:text-sm"
        >
          #{getMoodName(mood.code)}
        </span>
      ))}
    </span>
  );
}
