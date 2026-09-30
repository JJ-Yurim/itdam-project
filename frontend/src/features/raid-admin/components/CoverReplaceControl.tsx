import { useId, useState } from 'react';

import { Button, useToast } from '@/shared/components';

import { uploadBookCoverImage } from '../api';
import { getRaidAdminErrorMessage } from '../errors';

interface CoverReplaceControlProps {
  /** 지금 걸려 있는 표지. 없으면 비어 있음을 알린다. */
  currentImageUrl: string | null;
  /** 어떤 대상의 표지인지. 파일 입력 라벨과 이미지 대체 텍스트에 함께 쓴다. */
  targetName: string;
  onReplace: (imageAssetId: number) => Promise<unknown>;
  /** 교체가 언제부터 반영되는지 같은, 대상마다 다른 안내. */
  note?: string;
}

/**
 * 표지 교체 컨트롤. 보스 기본 표지와 개별 책 표지가 같은 절차(업로드 → 자산 ID로 교체)를
 * 쓰므로 한 컴포넌트로 둔다.
 */
export function CoverReplaceControl({
  currentImageUrl,
  targetName,
  onReplace,
  note,
}: CoverReplaceControlProps) {
  const [file, setFile] = useState<File | null>(null);
  // 업로드에 성공한 자산 ID는 남겨 둔다. 교체 호출만 실패해 다시 누를 때 같은 파일을
  // 또 올리면 어디에도 참조되지 않는 자산이 쌓인다.
  const [uploadedAssetId, setUploadedAssetId] = useState<number | null>(null);
  // 파일 입력은 비제어 요소라 상태만 비우면 DOM에 파일명이 남는다. key를 바꿔 다시 마운트한다.
  const [inputKey, setInputKey] = useState(0);
  const [isPending, setIsPending] = useState(false);
  const { showToast } = useToast();
  const inputId = useId();

  const replace = async () => {
    if (!file) {
      showToast({ message: '교체할 표지 이미지를 선택해 주세요.', variant: 'error' });
      return;
    }
    setIsPending(true);
    try {
      let assetId = uploadedAssetId;
      if (assetId === null) {
        try {
          assetId = (await uploadBookCoverImage(file)).imageAssetId;
          setUploadedAssetId(assetId);
        } catch (error) {
          showToast({
            message: getRaidAdminErrorMessage(error, '표지 이미지를 업로드하지 못했습니다.'),
            variant: 'error',
          });
          return;
        }
      }
      try {
        await onReplace(assetId);
      } catch (error) {
        showToast({
          message: getRaidAdminErrorMessage(error, '표지를 교체하지 못했습니다.'),
          variant: 'error',
        });
        return;
      }
      setFile(null);
      setUploadedAssetId(null);
      setInputKey((value) => value + 1);
      showToast({ message: '표지를 교체했습니다.', variant: 'success' });
    } finally {
      // 해제 지점을 한 곳에 모은다. 분기마다 풀면 나중에 갈래가 늘 때 하나를 빠뜨려
      // 버튼이 영영 잠긴 채로 남는다.
      setIsPending(false);
    }
  };

  return (
    <div className="mt-3 flex items-start gap-3 rounded-xl bg-stone-50 p-3">
      {currentImageUrl ? (
        <img
          src={currentImageUrl}
          alt={`${targetName} 현재 표지`}
          className="size-16 shrink-0 rounded-lg object-cover"
        />
      ) : (
        <div className="flex size-16 shrink-0 items-center justify-center rounded-lg bg-stone-200 text-xs text-stone-500">
          표지 없음
        </div>
      )}

      <div className="min-w-0 flex-1">
        <label htmlFor={inputId} className="block text-xs font-semibold text-stone-700">
          {targetName} 표지 교체
        </label>
        <input
          id={inputId}
          key={inputKey}
          type="file"
          accept="image/png,image/jpeg,image/webp"
          // 진행 중에는 선택을 바꾸지 못하게 한다. 업로드가 끝나기 전에 다른 파일을 고르면
          // 뒤늦게 도착한 응답이 uploadedAssetId를 덮어써, 화면에 보이는 파일과 다른
          // 이미지가 반영될 수 있다.
          disabled={isPending}
          onChange={(event) => {
            setFile(event.target.files?.[0] ?? null);
            setUploadedAssetId(null);
          }}
          className="mt-1 block w-full text-sm"
        />
        {note && <p className="mt-1 text-xs text-stone-500">{note}</p>}
        <Button
          type="button"
          variant="secondary"
          // 한 화면에 이 컨트롤이 대상 수만큼 놓인다. 이름이 모두 "표지 교체"면 버튼
          // 목록만으로는 어떤 대상의 것인지 가릴 수 없다.
          aria-label={`${targetName} 표지 교체`}
          className="mt-2"
          disabled={isPending}
          onClick={() => void replace()}
        >
          {isPending ? '교체 중...' : '표지 교체'}
        </Button>
      </div>
    </div>
  );
}
