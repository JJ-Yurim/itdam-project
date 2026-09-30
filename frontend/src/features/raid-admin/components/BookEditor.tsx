import { useState } from 'react';

import { Button, useToast } from '@/shared/components';

import { getRaidAdminErrorMessage, isConflictError } from '../errors';
import {
  useChangeBookCoverMutation,
  useUpdateAdminBookVisibilityMutation,
} from '../mutations';
import type { AdminBook } from '../types';
import { ConfirmDialog } from './ConfirmDialog';
import { CoverReplaceControl } from './CoverReplaceControl';

interface BookEditorProps {
  book: AdminBook;
  onClose: () => void;
}

/**
 * 책 관리 패널. 표지 교체와 공개 상태 전환은 서버 계약이 서로 다르고 각각 따로 실패할 수 있어
 * 한 버튼으로 묶지 않는다. 표지는 즉시 반영되고, 공개 전환은 확인 절차를 거친다.
 */
export function BookEditor({ book, onClose }: BookEditorProps) {
  // 목록 스냅샷을 그대로 두면 전환 후에도 옛 상태가 남아 버튼 문구가 뒤집히지 않는다.
  const [current, setCurrent] = useState(book);
  const [isConfirmOpen, setIsConfirmOpen] = useState(false);
  const coverMutation = useChangeBookCoverMutation();
  const visibilityMutation = useUpdateAdminBookVisibilityMutation();
  const { showToast } = useToast();

  const nextVisibility = current.visibility === 'PUBLIC' ? 'HIDDEN' : 'PUBLIC';
  const toggleLabel = nextVisibility === 'HIDDEN' ? '비공개로 전환' : '공개로 전환';
  const completed = current.status === 'COMPLETED';

  const changeVisibility = () => {
    setIsConfirmOpen(false);
    visibilityMutation.mutate(
      {
        bookId: current.bookId,
        request: { visibility: nextVisibility, expectedUpdatedAt: current.updatedAt },
      },
      {
        onSuccess: (updated) => {
          setCurrent(updated);
          showToast({
            message: nextVisibility === 'HIDDEN' ? '책을 비공개로 바꿨습니다.' : '책을 공개했습니다.',
            variant: 'success',
          });
        },
        onError: (error) =>
          showToast({
            message: getRaidAdminErrorMessage(error, '공개 상태를 바꾸지 못했습니다.'),
            variant: 'error',
          }),
      },
    );
  };

  return (
    <div className="space-y-6">
      {isConflictError(visibilityMutation.error) && (
        <div role="alert" className="rounded-xl bg-red-50 p-3 text-sm text-red-700">
          <p>다른 관리자가 먼저 이 책을 수정했습니다. 최신 내용을 확인한 뒤 다시 시도해 주세요.</p>
          <Button variant="secondary" className="mt-2" onClick={onClose}>
            최신 데이터 다시 불러오기
          </Button>
        </div>
      )}

      <section className="space-y-1 text-sm text-stone-600">
        <p className="font-bold text-stone-900">
          {current.bossName} <span className="text-xs font-normal text-stone-400">#{current.bookId}</span>
        </p>
        <p>수록 {current.contentCount}편</p>
      </section>

      <section>
        <h3 className="text-sm font-bold text-stone-900">표지</h3>
        <CoverReplaceControl
          currentImageUrl={current.coverImageUrl}
          targetName={`${current.bossName} 책`}
          onReplace={async (imageAssetId) => {
            const updated = await coverMutation.mutateAsync({ bookId: current.bookId, imageAssetId });
            // 표지 교체도 서버의 updatedAt을 올린다. 이어받지 않으면 다음 공개 전환이 409가 된다.
            setCurrent((previous) => ({
              ...previous,
              coverImageAssetId: updated.coverImageAssetId,
              coverImageUrl: updated.coverImageUrl,
              updatedAt: updated.updatedAt,
            }));
          }}
        />
      </section>

      <section className="space-y-2">
        <h3 className="text-sm font-bold text-stone-900">공개 상태</h3>
        {completed ? (
          <>
            <p className="text-xs text-stone-500">
              {current.visibility === 'PUBLIC'
                ? '지금은 도서관과 피드에 공개돼 있습니다.'
                : '지금은 도서관과 피드에서 숨겨져 있습니다.'}
            </p>
            <Button
              variant="secondary"
              disabled={visibilityMutation.isPending}
              onClick={() => setIsConfirmOpen(true)}
            >
              {visibilityMutation.isPending ? '전환 중...' : toggleLabel}
            </Button>
          </>
        ) : (
          <p className="rounded-lg bg-stone-100 p-2 text-xs text-stone-600">
            생성이 끝난 책만 공개 상태를 바꿀 수 있습니다.
          </p>
        )}
      </section>

      <ConfirmDialog
        open={isConfirmOpen}
        title={nextVisibility === 'HIDDEN' ? '책 비공개 전환' : '책 공개 전환'}
        description={
          nextVisibility === 'HIDDEN'
            ? `${current.bossName} #${current.bookId} · 도서관과 피드에서 숨겨집니다. 기여 기록과 반응은 그대로 남습니다.`
            : `${current.bossName} #${current.bookId} · 도서관과 피드에 다시 노출됩니다.`
        }
        confirmLabel={toggleLabel}
        destructive={nextVisibility === 'HIDDEN'}
        onConfirm={changeVisibility}
        onCancel={() => setIsConfirmOpen(false)}
      />
    </div>
  );
}
