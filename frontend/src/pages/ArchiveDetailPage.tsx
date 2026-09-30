import { useState } from 'react';

import { Link, useNavigate, useParams } from 'react-router-dom';

import {
  formatArchiveDate,
  getMoodName,
  useArchiveDetailQuery,
  useArchiveEvaluationQuery,
  useDeleteArchiveSentenceMutation,
} from '@/features/archive';
import { Button, ErrorState, LoadingState, Modal } from '@/shared/components';

const scoreNames = {
  completeness: '완결성',
  originality: '독창성',
  expression: '표현력',
  imagery: '심상',
  emotionalImpact: '정서',
} as const;

export function ArchiveDetailPage() {
  const navigate = useNavigate();
  const [isDeleteModalOpen, setIsDeleteModalOpen] = useState(false);
  const { sentenceVersionId: sentenceVersionIdParam } = useParams();
  const parsedId = Number(sentenceVersionIdParam);
  const sentenceVersionId = Number.isInteger(parsedId) && parsedId > 0 ? parsedId : null;
  const detailQuery = useArchiveDetailQuery(sentenceVersionId);
  const evaluationQuery = useArchiveEvaluationQuery(sentenceVersionId);
  const deleteMutation = useDeleteArchiveSentenceMutation();

  if (sentenceVersionId === null) {
    return (
      <ErrorState title="잘못된 문장 주소입니다" description="내 서고에서 문장을 선택해 주세요." />
    );
  }

  // 세부 점수(평가 조회)는 부가 정보이므로 상세 조회만 화면 전체의 로딩·오류 기준으로 삼는다.
  if (detailQuery.isPending) {
    return <LoadingState title="문장 기록을 불러오고 있어요" />;
  }

  if (detailQuery.isError) {
    return (
      <ErrorState
        title="문장 기록을 불러오지 못했어요"
        description="문장이 없거나 조회할 수 없는 상태입니다."
        onRetry={() => void detailQuery.refetch()}
      />
    );
  }

  const sentence = detailQuery.data;
  const evaluation = evaluationQuery.data;
  const scores = evaluation?.scores ?? null;

  return (
    <div className="space-y-6">
      <header>
        <Link to="/archive" className="text-sm font-semibold text-violet-700 hover:underline">
          ← 내 서고로 돌아가기
        </Link>
        <div className="mt-4 flex flex-wrap items-center gap-2">
          <h1 className="text-2xl font-extrabold text-stone-900">문장 상세</h1>
          <span className="rounded-full bg-stone-100 px-2.5 py-1 text-xs text-stone-600">
            버전 {sentence.versionNo}
          </span>
        </div>
        <p className="mt-2 text-xs text-stone-500">
          {formatArchiveDate(sentence.evaluatedAt)} 평가 완료
        </p>
      </header>

      <section
        aria-labelledby="archive-content-title"
        className="rounded-2xl border border-stone-200 bg-white p-5"
      >
        <h2 id="archive-content-title" className="text-sm font-bold text-stone-900">
          원문
        </h2>
        <p className="mt-3 whitespace-pre-wrap break-words text-base leading-7 text-stone-800">
          {sentence.content}
        </p>
        <p className="mt-4 text-xs font-semibold text-stone-500">
          평가 완료 문장은 수정할 수 없어요.
        </p>
      </section>

      <section
        aria-labelledby="archive-score-title"
        className="rounded-2xl border border-violet-100 bg-violet-50/60 p-5"
      >
        <h2 id="archive-score-title" className="font-bold text-stone-900">
          AI 평가
        </h2>
        <dl className="mt-4 grid grid-cols-3 gap-2">
          <div className="rounded-xl bg-white p-3 text-center">
            <dt className="text-xs text-stone-500">기본 점수</dt>
            <dd className="mt-1 text-xl font-extrabold text-violet-800">{sentence.baseScore}</dd>
          </div>
          <div className="rounded-xl bg-white p-3 text-center">
            <dt className="text-xs text-stone-500">단어 보너스</dt>
            <dd className="mt-1 text-xl font-extrabold text-emerald-700">+{sentence.wordBonus}</dd>
          </div>
          <div className="rounded-xl bg-white p-3 text-center">
            <dt className="text-xs text-stone-500">공격력</dt>
            <dd className="mt-1 text-xl font-extrabold text-orange-700">
              {sentence.gameBaseAttack}
            </dd>
          </div>
        </dl>

        {evaluationQuery.isPending && (
          <p role="status" className="mt-3 rounded-xl bg-white p-4 text-sm text-stone-500">
            세부 점수를 불러오고 있어요.
          </p>
        )}

        {evaluationQuery.isError && (
          <div className="mt-3 flex items-center justify-between gap-3 rounded-xl bg-white p-4">
            <p className="text-sm text-stone-600">세부 점수를 불러오지 못했어요.</p>
            <button
              type="button"
              onClick={() => void evaluationQuery.refetch()}
              className="shrink-0 text-sm font-semibold text-violet-700 underline underline-offset-2"
            >
              다시 시도
            </button>
          </div>
        )}

        {scores && (
          <dl className="mt-3 grid grid-cols-5 gap-1.5">
            {Object.entries(scoreNames).map(([key, label]) => (
              <div key={key} className="rounded-lg bg-white p-2 text-center">
                <dt className="text-[11px] text-stone-500">{label}</dt>
                <dd className="mt-1 text-sm font-bold text-stone-800">
                  {scores[key as keyof typeof scoreNames]}
                </dd>
              </div>
            ))}
          </dl>
        )}

        {evaluation && !scores && (
          <p className="mt-3 rounded-xl bg-white p-4 text-sm text-stone-600">
            세부 점수가 아직 준비되지 않았어요. 평가가 완료되면 표시됩니다.
          </p>
        )}

        {evaluation?.overallFeedback && (
          <p className="mt-4 rounded-xl bg-white p-4 text-sm leading-6 text-stone-700">
            {evaluation.overallFeedback}
          </p>
        )}
      </section>

      <section
        aria-labelledby="archive-mood-title"
        className="rounded-2xl border border-stone-200 bg-white p-5"
      >
        <h2 id="archive-mood-title" className="font-bold text-stone-900">
          분위기와 사용 단어
        </h2>
        <div className="mt-3 flex flex-wrap gap-2">
          {sentence.moods.map((mood) => (
            <span
              key={mood.code}
              className="rounded-full bg-violet-100 px-3 py-1.5 text-xs font-semibold text-violet-800"
            >
              {getMoodName(mood.code)} {Math.round(mood.strength * 100)}%
            </span>
          ))}
          {sentence.usedDailyWords.map((word) => (
            <span
              key={word.dailyWordSetItemId}
              className="rounded-full bg-emerald-100 px-3 py-1.5 text-xs font-semibold text-emerald-800"
            >
              #{word.word}
            </span>
          ))}
        </div>
      </section>

      <section
        aria-labelledby="archive-history-title"
        className="rounded-2xl border border-stone-200 bg-white p-5"
      >
        <h2 id="archive-history-title" className="font-bold text-stone-900">
          창작 이력
        </h2>
        <div className="mt-4 space-y-3 text-sm text-stone-700">
          {sentence.bossSubmissions.length === 0 && sentence.bookInclusions.length === 0 && (
            <p className="text-stone-500">아직 원정 제출이나 공동 책 수록 이력이 없어요.</p>
          )}
          {sentence.bossSubmissions.map((submission) => (
            <div key={submission.submissionId} className="rounded-xl bg-red-50 p-3">
              <strong>{submission.bossName} 원정 제출</strong>
              <p className="mt-1 text-xs text-stone-600">
                최종 피해 {submission.finalDamage} · {formatArchiveDate(submission.submittedAt)}
              </p>
              <Link
                to={`/boss/${submission.bossRunId}`}
                className="mt-2 inline-flex min-h-11 items-center text-xs font-semibold text-red-700 hover:underline"
              >
                관련 원정 보기
              </Link>
            </div>
          ))}
          {sentence.bookInclusions.map((book) => (
            <div key={book.bookId} className="rounded-xl bg-emerald-50 p-3">
              <strong>공동 책 #{book.bookId} 수록</strong>
              <p className="mt-1 text-xs text-stone-600">수록 순서 {book.sequenceNo}</p>
              <Link
                to={`/library/books/${book.bookId}`}
                className="mt-2 inline-flex min-h-11 items-center text-xs font-semibold text-emerald-700 hover:underline"
              >
                공동 책 보기
              </Link>
            </div>
          ))}
        </div>
      </section>

      <section className="rounded-2xl border border-red-100 bg-red-50/50 p-5">
        <h2 className="font-bold text-stone-900">문장 관리</h2>
        <p className="mt-2 text-sm leading-6 text-stone-600">
          문장을 삭제하면 내 서고에서는 사라지지만 기존 원정 제출과 공동 책 수록 기록은
          유지됩니다.
        </p>
        <Button variant="danger" className="mt-4" onClick={() => setIsDeleteModalOpen(true)}>
          문장 삭제
        </Button>
      </section>

      <Modal
        open={isDeleteModalOpen}
        title="문장을 삭제할까요?"
        onClose={() => {
          if (!deleteMutation.isPending) {
            setIsDeleteModalOpen(false);
          }
        }}
        footer={
          <>
            <Button
              variant="secondary"
              disabled={deleteMutation.isPending}
              onClick={() => setIsDeleteModalOpen(false)}
            >
              취소
            </Button>
            <Button
              variant="danger"
              disabled={deleteMutation.isPending}
              onClick={() =>
                deleteMutation.mutate(
                  { sentenceId: sentence.sentenceId, sentenceVersionId },
                  { onSuccess: () => void navigate('/archive', { replace: true }) },
                )
              }
            >
              {deleteMutation.isPending ? '삭제 중…' : '삭제하기'}
            </Button>
          </>
        }
      >
        <p className="line-clamp-3 whitespace-pre-wrap rounded-xl bg-stone-50 p-3 text-sm text-stone-700">
          {sentence.content}
        </p>
        <p className="mt-3 text-sm leading-6 text-red-700">
          삭제 후에도 이미 제출한 원정과 공동 책의 문장 참조는 유지됩니다. 이 작업은 내 서고에서
          되돌릴 수 없습니다.
        </p>
        {deleteMutation.isError && (
          <p role="alert" className="mt-3 text-sm font-semibold text-red-700">
            문장을 삭제하지 못했어요. 잠시 후 다시 시도해 주세요.
          </p>
        )}
      </Modal>
    </div>
  );
}
