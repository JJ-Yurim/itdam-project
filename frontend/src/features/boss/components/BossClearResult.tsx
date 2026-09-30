import type { ReactNode } from 'react';

import { Badge } from '@/shared/components';

import type { BossSubmissionSummary } from '../types';

interface BossClearResultProps {
  bossName: string;
  clearedAt: string | null;
  accumulatedDamage: number;
  mySubmissionCount: number;
  mySubmissions: BossSubmissionSummary[];
  bookGeneration: ReactNode;
  footerAction?: ReactNode;
}

function findBestSubmission(submissions: BossSubmissionSummary[]) {
  if (submissions.length === 0) return null;

  return submissions.reduce((best, submission) =>
    submission.finalDamage > best.finalDamage ? submission : best,
  );
}

export function BossClearResult({
  bossName,
  clearedAt,
  accumulatedDamage,
  mySubmissionCount,
  mySubmissions,
  bookGeneration,
  footerAction,
}: BossClearResultProps) {
  const bestSubmission = findBestSubmission(mySubmissions);
  const myTotalDamage = mySubmissions.reduce(
    (total, submission) => total + submission.finalDamage,
    0,
  );
  const contributionPercent =
    accumulatedDamage > 0 ? Math.round((myTotalDamage / accumulatedDamage) * 100) : null;

  return (
    <article>
      <section className="rounded-3xl border border-stone-200 bg-white p-8 text-center shadow-sm">
        <div
          aria-hidden="true"
          className="mx-auto flex size-20 items-center justify-center rounded-full bg-amber-50 text-4xl ring-4 ring-amber-100"
        >
          👑
        </div>
        <h1 className="mt-4 text-2xl font-extrabold text-stone-900">집필 완료</h1>
        <p className="mt-2 text-lg font-bold text-stone-800">『{bossName}』</p>
        <p className="mt-2 text-sm leading-6 text-stone-600">
          {clearedAt && `${new Date(clearedAt).toLocaleString('ko-KR')}에 클리어했어요.`}
        </p>
        <p className="mt-4 rounded-xl bg-violet-50 px-4 py-3 text-sm leading-6 text-violet-900">
          이 원정은 종료되어 새 문장을 제출할 수 없어요. 제출된 문장들로 공동 책 생성이
          시작됐습니다.
        </p>
      </section>

      {bestSubmission && (
        <section aria-labelledby="best-sentence-title" className="mt-6">
          <h2 id="best-sentence-title" className="font-bold text-stone-900">
            나의 베스트 문장
          </h2>
          <blockquote className="mt-3 rounded-2xl border-2 border-violet-200 bg-white p-5 shadow-sm">
            <div className="flex items-center justify-between gap-3">
              <Badge tone="like">BEST</Badge>
              <span className="text-sm font-bold text-violet-700">
                {bestSubmission.finalDamage.toLocaleString()} 데미지
              </span>
            </div>
            <p className="mt-3 whitespace-pre-wrap break-words text-sm leading-6 text-stone-800">
              “{bestSubmission.content}”
            </p>
          </blockquote>
        </section>
      )}

      <section aria-labelledby="contribution-title" className="mt-6">
        <h2 id="contribution-title" className="font-bold text-stone-900">
          나의 기여도
        </h2>
        <dl className="mt-3 grid grid-cols-3 gap-3">
          <div className="rounded-2xl border border-stone-200 bg-white p-4 text-center">
            <dt className="text-xs font-semibold text-stone-500">제출 횟수</dt>
            <dd className="mt-1 text-xl font-extrabold text-violet-700">
              {mySubmissionCount}회
            </dd>
          </div>
          <div className="rounded-2xl border border-stone-200 bg-white p-4 text-center">
            <dt className="text-xs font-semibold text-stone-500">총 데미지</dt>
            <dd className="mt-1 text-xl font-extrabold text-blue-700">
              {myTotalDamage.toLocaleString()}
            </dd>
          </div>
          <div className="rounded-2xl border border-stone-200 bg-white p-4 text-center">
            <dt className="text-xs font-semibold text-stone-500">기여율</dt>
            <dd className="mt-1 text-xl font-extrabold text-emerald-700">
              {contributionPercent === null ? '-' : `${contributionPercent}%`}
            </dd>
          </div>
        </dl>
      </section>

      <section aria-labelledby="my-submissions-title" className="mt-6">
        <h2 id="my-submissions-title" className="font-bold text-stone-900">
          내가 참여한 문장
        </h2>
        {mySubmissions.length === 0 ? (
          <p className="mt-3 rounded-xl bg-stone-50 p-4 text-sm text-stone-600">
            이번 원정에 제출한 문장이 없어요.
          </p>
        ) : (
          <ul className="mt-3 space-y-2">
            {mySubmissions.map((submission) => (
              <li
                key={submission.submissionId}
                className="rounded-xl border border-stone-200 bg-white p-4"
              >
                <p className="whitespace-pre-wrap break-words text-sm leading-6 text-stone-800">
                  {submission.content}
                </p>
                <div className="mt-3 flex items-center justify-between gap-3 border-t border-stone-100 pt-3 text-sm">
                  <span className="font-semibold text-stone-800">
                    최종 피해 {submission.finalDamage.toLocaleString()}
                  </span>
                  <span className="text-xs text-stone-500">
                    {new Date(submission.submittedAt).toLocaleString('ko-KR')}
                  </span>
                </div>
              </li>
            ))}
          </ul>
        )}
      </section>

      <section aria-labelledby="book-generation-title" className="mt-6">
        <h2 id="book-generation-title" className="font-bold text-stone-900">
          공동 책 생성 상태
        </h2>
        <div className="mt-3">{bookGeneration}</div>
      </section>
      {footerAction}
    </article>
  );
}
