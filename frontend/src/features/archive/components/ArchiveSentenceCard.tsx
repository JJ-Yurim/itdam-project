import { Link } from 'react-router-dom';

import { Badge } from '@/shared/components';

import { formatArchiveDate, getMoodName } from '../format';
import type { ArchiveSentence } from '../types';

interface ArchiveSentenceCardProps {
  sentence: ArchiveSentence;
}

export function ArchiveSentenceCard({ sentence }: ArchiveSentenceCardProps) {
  const representativeMood = [...sentence.moods].sort((a, b) => a.rank - b.rank)[0];

  return (
    <article className="rounded-2xl border border-stone-200 bg-white p-5 shadow-sm">
      <div className="flex flex-wrap items-center gap-2 text-xs font-semibold">
        <Badge tone="brand">AI {sentence.baseScore}점</Badge>
        <Badge tone="attack">공격력 {sentence.gameBaseAttack}</Badge>
        <span className="ml-auto text-stone-500">{formatArchiveDate(sentence.evaluatedAt)}</span>
      </div>

      <p className="mt-4 line-clamp-3 whitespace-pre-wrap break-words text-sm leading-6 text-stone-800">
        {sentence.content}
      </p>

      <div className="mt-4 flex flex-wrap items-center gap-2 text-xs">
        <span className="text-stone-500">버전 {sentence.versionNo}</span>
        {representativeMood && <Badge>#{getMoodName(representativeMood.code)}</Badge>}
        {/* 제출 완료는 오류가 아니라 진행 상태라 위험색 대신 브랜드 톤으로 알린다. */}
        {sentence.bossSubmissions.length > 0 && <Badge tone="brand">원정 제출 완료</Badge>}
        {sentence.bookInclusions.length > 0 && <Badge tone="success">공동 책 수록</Badge>}
      </div>

      <Link
        to={`/archive/${sentence.sentenceVersionId}`}
        className="mt-4 inline-flex min-h-11 items-center font-semibold text-violet-700 underline-offset-4 hover:underline"
      >
        상세 보기
      </Link>
    </article>
  );
}
