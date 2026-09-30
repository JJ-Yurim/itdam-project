import { useState } from 'react';

import {
  useArchiveDetailQuery,
  useArchiveEvaluationQuery,
  type ArchiveEvaluationScores,
} from '@/features/archive';
import { Modal } from '@/shared/components';

import type { BossSubmissionSummary } from '../types';

interface BossSubmissionHistoryProps {
  submissions: BossSubmissionSummary[];
  /**
   * 탭 라벨이 제목을 대신할 때 제목을 시각적으로만 숨긴다.
   * 섹션 이름은 접근성 트리에 남겨 두어야 하므로 제거하지 않는다.
   */
  headingHidden?: boolean;
}

const scoreNames: Record<keyof Omit<ArchiveEvaluationScores, 'baseScore'>, string> = {
  completeness: '완결성',
  originality: '독창성',
  expression: '표현력',
  imagery: '심상',
  emotionalImpact: '정서',
};

const submittedAtFormatter = new Intl.DateTimeFormat('ko-KR', {
  dateStyle: 'medium',
  timeStyle: 'short',
});

// Intl은 Invalid Date에 RangeError를 던진다. 시각 하나 때문에 이력 전체가
// 렌더링되지 않는 것을 막는다.
function formatSubmittedAt(value: string) {
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? '제출 시각 정보 없음' : submittedAtFormatter.format(date);
}

export function BossSubmissionHistory({
  submissions,
  headingHidden = false,
}: BossSubmissionHistoryProps) {
  const [selectedSubmission, setSelectedSubmission] = useState<BossSubmissionSummary | null>(null);

  return (
    <section aria-labelledby="submission-history-title" className={headingHidden ? '' : 'mt-8'}>
      <div className="flex items-center justify-between gap-3">
        <h2
          id="submission-history-title"
          className={
            headingHidden ? 'sr-only' : 'text-sm font-bold text-stone-900'
          }
        >
          내 제출 이력
        </h2>
        <span className="text-xs text-stone-500">제출 {submissions.length}개</span>
      </div>

      {submissions.length === 0 ? (
        <p className="mt-3 rounded-xl bg-stone-50 p-4 text-sm text-stone-600">
          아직 이 원정에 제출한 문장이 없어요.
        </p>
      ) : (
        <ul aria-label="내 제출 이력 목록" className="mt-3 space-y-2">
          {submissions.map((submission) => (
            <BossSubmissionHistoryItem
              key={submission.submissionId}
              submission={submission}
              onOpen={() => setSelectedSubmission(submission)}
            />
          ))}
        </ul>
      )}

      {selectedSubmission !== null && (
        <BossSubmissionEvaluationModal
          submission={selectedSubmission}
          onClose={() => setSelectedSubmission(null)}
        />
      )}
    </section>
  );
}

interface BossSubmissionHistoryItemProps {
  submission: BossSubmissionSummary;
  onOpen: () => void;
}

function BossSubmissionHistoryItem({ submission, onOpen }: BossSubmissionHistoryItemProps) {
  // 본문은 목록 응답(mySubmissions)에 함께 담겨 온다. 항목마다 상세를 부르면
  // 제출 수만큼 요청이 늘어난다.
  const submittedAt = formatSubmittedAt(submission.submittedAt);

  // 피해량과 제출 시각은 목록을 간결하게 두고 팝업에서 보여준다.
  return (
    <li className="rounded-xl border border-stone-200 bg-white p-4">
      <div className="flex items-center justify-between gap-3">
        <p className="min-w-0 flex-1 whitespace-pre-wrap break-words text-sm font-bold leading-6 text-stone-800">
          {submission.content}
        </p>

        <button
          type="button"
          onClick={onOpen}
          // 이력이 여러 건이면 버튼 이름이 모두 같아 스크린 리더가 구분하지 못한다.
          aria-label={`${submittedAt} 제출 문장 자세히 보기`}
          className="min-h-11 shrink-0 px-1 text-xs font-semibold text-violet-700 hover:text-violet-800"
        >
          자세히
        </button>
      </div>
    </li>
  );
}

interface BossSubmissionEvaluationModalProps {
  submission: BossSubmissionSummary;
  onClose: () => void;
}

function BossSubmissionEvaluationModal({
  submission,
  onClose,
}: BossSubmissionEvaluationModalProps) {
  // 본문은 목록에 이미 있어 바로 보여주고, 점수 구성은 상세에서 가져온다.
  // 모달을 열 때 한 번만 조회하므로 항목 수에 비례하지 않는다.
  const detailQuery = useArchiveDetailQuery(submission.sentenceVersionId);
  const evaluationQuery = useArchiveEvaluationQuery(submission.sentenceVersionId);
  const sentence = detailQuery.data;
  const evaluation = evaluationQuery.data;
  const scores = evaluation?.scores;
  const summaryScores = evaluation
    ? {
        baseScore: evaluation.scores?.baseScore ?? sentence?.baseScore ?? '-',
        wordBonus: evaluation.wordBonus ?? sentence?.wordBonus ?? null,
        gameBaseAttack: evaluation.gameBaseAttack ?? sentence?.gameBaseAttack ?? '-',
      }
    : null;

  return (
    <Modal open title="제출 결과" onClose={onClose}>
      {/* 문장은 바로 뒤 목록에 그대로 보이므로 팝업에서는 결과만 다룬다. */}
      <dl className="flex items-end justify-between gap-3 rounded-xl bg-violet-50 px-4 py-3">
        <div>
          <dt className="text-xs text-stone-500">최종 피해</dt>
          <dd className="mt-1 text-2xl font-extrabold text-violet-800">
            {submission.finalDamage.toLocaleString()}
          </dd>
        </div>

        <div className="text-right">
          <dt className="text-xs text-stone-500">제출 시각</dt>
          <dd className="mt-1 text-sm font-semibold text-stone-700">
            {formatSubmittedAt(submission.submittedAt)}
          </dd>
        </div>
      </dl>

      <section
        aria-labelledby="submission-evaluation-title"
        className="mt-4 rounded-2xl bg-violet-50/60 p-4"
      >
        <h3 id="submission-evaluation-title" className="font-bold text-stone-900">
          AI 평가
        </h3>

        {evaluationQuery.isPending ? (
          <p role="status" className="mt-3 rounded-xl bg-white p-4 text-sm text-stone-500">
            평가 결과를 불러오고 있어요.
          </p>
        ) : evaluationQuery.isError ? (
          <div className="mt-3 rounded-xl bg-white p-4">
            <p className="text-sm text-stone-600">평가 결과를 불러오지 못했어요.</p>
            <button
              type="button"
              onClick={() => {
                void evaluationQuery.refetch();
              }}
              className="mt-2 min-h-11 text-sm font-semibold text-violet-700 underline underline-offset-2"
            >
              다시 시도
            </button>
          </div>
        ) : evaluation && summaryScores ? (
          <>
            <dl className="mt-4 grid grid-cols-3 gap-2">
              <ScoreCard label="기본 점수" value={summaryScores.baseScore} tone="text-violet-800" />
              <ScoreCard
                label="단어 보너스"
                value={summaryScores.wordBonus === null ? '-' : `+${summaryScores.wordBonus}`}
                tone="text-emerald-700"
              />
              <ScoreCard
                label="공격력"
                value={summaryScores.gameBaseAttack}
                tone="text-orange-700"
              />
            </dl>

            {scores && (
              <dl className="mt-3 grid grid-cols-5 gap-1.5">
                {Object.entries(scoreNames).map(([key, label]) => (
                  <ScoreCard
                    key={key}
                    label={label}
                    value={scores[key as keyof typeof scoreNames]}
                    compact
                  />
                ))}
              </dl>
            )}

            {evaluation.overallFeedback && (
              <p className="mt-4 rounded-xl bg-white p-4 text-sm leading-6 text-stone-700">
                {evaluation.overallFeedback}
              </p>
            )}
          </>
        ) : null}
      </section>
    </Modal>
  );
}

interface ScoreCardProps {
  label: string;
  value: number | string;
  tone?: string;
  compact?: boolean;
}

function ScoreCard({ label, value, tone = 'text-stone-800', compact = false }: ScoreCardProps) {
  return (
    <div className={['rounded-xl bg-white text-center', compact ? 'p-2' : 'p-3'].join(' ')}>
      <dt className={compact ? 'text-[11px] text-stone-500' : 'text-xs text-stone-500'}>{label}</dt>
      <dd className={['mt-1 font-extrabold', compact ? 'text-sm' : 'text-xl', tone].join(' ')}>
        {value}
      </dd>
    </div>
  );
}
