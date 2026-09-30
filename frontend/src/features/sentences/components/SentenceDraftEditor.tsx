import { useCallback, useEffect, useMemo, useRef, useState } from 'react';

import type { ReactNode } from 'react';

import { zodResolver } from '@hookform/resolvers/zod';
import { useForm, useWatch } from 'react-hook-form';
import { useBeforeUnload, useBlocker, useSearchParams } from 'react-router-dom';
import { z } from 'zod';

import { useCurrentUserQuery } from '@/features/auth';
import { useRevealedWords, useTodayDailyWordsQuery } from '@/features/daily-words';
import type { DailyWord } from '@/features/daily-words';
import { Button, ErrorState, LoadingState } from '@/shared/components';

import { getMatchedDailyWordIds } from '../matching';
import {
  useCreateSentenceDraftMutation,
  useCreateSentenceVersionMutation,
  useSentenceVersionQuery,
  useUpdateSentenceDraftMutation,
} from '../queries';
import type { SentenceVersionResponse } from '../types';

import { DraftWordPack } from './DraftWordPack';
import { MatchedSentencePreview } from './MatchedSentencePreview';

const AUTO_SAVE_DELAY_MS = 1000;
const MAX_CONTENT_LENGTH = 500;

const draftSchema = z.object({
  content: z
    .string()
    .max(MAX_CONTENT_LENGTH, `문장은 ${MAX_CONTENT_LENGTH}자까지 작성할 수 있어요.`)
    .refine((value) => value.length === 0 || value.trim().length > 0, '공백만 저장할 수 없어요.'),
});

type DraftFormValues = z.infer<typeof draftSchema>;

type SaveState = 'idle' | 'saving' | 'saved' | 'error';

export type DraftSaveMode = 'auto' | 'manual';

export interface SentenceDraftEditorContext {
  versionId: number | null;
  content: string;
  dailyWords: DailyWord[];
  matchedWordIds: Set<number>;
  hasUnsavedChanges: boolean;
  isSaving: boolean;
  isEditable: boolean;
  sentenceVersion: SentenceVersionResponse | undefined;
  isRevisionMode: boolean;
  startRevision: () => void;
  cancelRevision: () => void;
  saveMode: DraftSaveMode;
  /** 저장 성공 시 저장된 버전 id를, 저장하지 않았거나 실패하면 null을 반환한다. */
  saveDraft: () => Promise<number | null>;
  /**
   * 평가를 마친 버전에서 에디터를 떼어내 다음 문장을 곧바로 쓸 수 있게 한다.
   * 평가한 내용 그대로면 비우고, 평가를 기다리는 동안 이미 다음 문장을 쓰기
   * 시작했다면 그 입력은 지우지 않는다.
   */
  detachAfterEvaluation: (evaluatedContent: string) => void;
  /**
   * 이미 떠난 버전을 다시 열어 수정 모드로 들어간다.
   * 거부된 문장을 고쳐 쓸 때 쓰며, 저장하면 같은 문장의 다음 버전이 된다.
   */
  reopenForRevision: (versionId: number) => void;
}

interface SentenceDraftEditorProps {
  title: string;
  description: string;
  /** auto: 입력 멈춤 시 자동 저장(기본) / manual: 서고에 저장 버튼으로만 저장 */
  saveMode?: DraftSaveMode;
  renderActions?: (context: SentenceDraftEditorContext) => ReactNode;
}

export function SentenceDraftEditor({
  title,
  description,
  saveMode = 'auto',
  renderActions,
}: SentenceDraftEditorProps) {
  const [searchParams, setSearchParams] = useSearchParams();
  const versionIdParam = searchParams.get('versionId');
  const parsedVersionId = versionIdParam === null ? null : Number(versionIdParam);
  const hasInvalidVersionId =
    parsedVersionId !== null && (!Number.isInteger(parsedVersionId) || parsedVersionId <= 0);
  const initialVersionId = hasInvalidVersionId ? null : parsedVersionId;

  const [versionId, setVersionId] = useState<number | null>(initialVersionId);
  const [saveState, setSaveState] = useState<SaveState>('idle');
  const [lastSavedContent, setLastSavedContent] = useState('');
  const [isRevisionMode, setIsRevisionMode] = useState(false);
  const restoredVersionIdRef = useRef<number | null>(null);
  const failedContentRef = useRef<string | null>(null);
  // 평가 뒤 에디터를 비우고 나서 거부된 문장을 다시 열 때, 복원이 끝난 시점에
  // 수정 모드로 들어가야 한다. 복원 effect가 수정 모드를 끄므로 의도를 ref로 넘긴다.
  const pendingRevisionVersionIdRef = useRef<number | null>(null);
  // 디바운스 자동 저장과 수동 저장 버튼이 연달아 실행돼도 같은 내용이
  // 중복 저장되지 않도록, 진행 중 여부와 마지막 저장 내용을 ref로 추적한다.
  // (예약된 타이머가 이전 렌더의 stale closure로 saveDraft를 호출하는 경우 방어)
  const inFlightSaveRef = useRef(false);
  const lastSavedContentRef = useRef('');
  // in-flight 중에 들어온 저장 요청은 버리지 않고 기록해 두었다가
  // 진행 중 저장이 끝난 뒤 이어서 실행한다 (아래 pending 재실행 effect).
  const [hasPendingSave, setHasPendingSave] = useState(false);

  const dailyWordsQuery = useTodayDailyWordsQuery();
  const sentenceVersionQuery = useSentenceVersionQuery(initialVersionId);
  const currentUserQuery = useCurrentUserQuery();
  const { mutateAsync: createDraft, isPending: isCreatingDraft } = useCreateSentenceDraftMutation();
  const { mutateAsync: createVersion, isPending: isCreatingVersion } =
    useCreateSentenceVersionMutation();
  const { mutateAsync: updateDraft, isPending: isUpdatingDraft } = useUpdateSentenceDraftMutation();

  const {
    control,
    register,
    reset,
    formState: { errors },
  } = useForm<DraftFormValues>({
    resolver: zodResolver(draftSchema),
    defaultValues: { content: '' },
    mode: 'onChange',
  });

  const content = useWatch({ control, name: 'content' }) ?? '';
  // 평가 결과가 도착한 시점의 최신 입력을 콜백에서 읽어야 한다.
  // 평가를 보낼 때 닫힌 클로저의 content로는 그 사이의 입력을 알 수 없다.
  const contentRef = useRef(content);
  useEffect(() => {
    contentRef.current = content;
  }, [content]);
  const isSaving = isCreatingDraft || isCreatingVersion || isUpdatingDraft;
  const hasUnsavedChanges = content !== lastSavedContent;
  const isContentEmpty = content.trim().length === 0;
  const hasUnsavedContent = hasUnsavedChanges && !isContentEmpty;
  // 수정 저장 직후처럼 versionId와 조회된 버전이 어긋난 순간에는 이전 버전
  // 데이터로 판정하지 않도록, versionId와 일치하는 버전만 기준으로 삼는다.
  const matchedVersion =
    sentenceVersionQuery.data?.id === versionId ? sentenceVersionQuery.data : undefined;
  const isEditable =
    matchedVersion == null || matchedVersion.evaluationStatus === 'DRAFT' || isRevisionMode;

  useEffect(() => {
    const restoredDraft = sentenceVersionQuery.data;

    if (!restoredDraft || restoredVersionIdRef.current === restoredDraft.id) {
      return;
    }

    const isReopenedForRevision = pendingRevisionVersionIdRef.current === restoredDraft.id;
    pendingRevisionVersionIdRef.current = null;

    restoredVersionIdRef.current = restoredDraft.id;
    lastSavedContentRef.current = restoredDraft.content;
    setLastSavedContent(restoredDraft.content);
    setVersionId(restoredDraft.id);
    setIsRevisionMode(isReopenedForRevision);
    reset({ content: restoredDraft.content });
    setSaveState('saved');
  }, [reset, sentenceVersionQuery.data]);

  const saveDraft = useCallback(async (): Promise<number | null> => {
    const contentToSave = content;
    const currentVersion = matchedVersion;

    if (!contentToSave.trim() || content.length > MAX_CONTENT_LENGTH || isSaving) {
      return null;
    }

    // 진행 중인 저장이 있으면(연타·타이머 중복 실행) 버리지 않고 pending으로
    // 기록한다. 진행 중 저장이 끝나면 최신 내용 기준으로 이어서 저장된다.
    if (inFlightSaveRef.current) {
      setHasPendingSave(true);
      return null;
    }

    // 마지막으로 저장한 내용과 동일하면(수동 저장 직후 예약돼 있던 자동 저장이
    // 늦게 실행되는 경우) 요청을 만들지 않고 이미 저장된 버전 id를 돌려준다.
    // 수정 모드는 같은 내용도 새 버전 생성이 목적이라 예외.
    if (!isRevisionMode && contentToSave === lastSavedContentRef.current) {
      return versionId;
    }

    // 수정 모드에서는 기준 버전 정보 없이 저장하면 DRAFT가 아닌 버전에
    // PATCH가 나가므로 저장하지 않고, 입력이 조용히 유실되지 않도록
    // 실패 상태로 전환해 재시도 흐름을 타게 한다.
    if (isRevisionMode && !currentVersion) {
      failedContentRef.current = contentToSave;
      setSaveState('error');
      return null;
    }

    inFlightSaveRef.current = true;
    setSaveState('saving');

    try {
      let savedDraft: SentenceVersionResponse;

      if (isRevisionMode && currentVersion) {
        savedDraft = await createVersion({
          sentenceId: currentVersion.sentenceId,
          content: contentToSave,
        });
      } else if (versionId === null) {
        savedDraft = await createDraft(contentToSave);
      } else {
        savedDraft = await updateDraft({ versionId, content: contentToSave });
      }

      // 저장 성공으로 sentenceVersionQuery 캐시가 채워져도 복원 effect가
      // 저장 시점 스냅샷으로 폼을 되돌리지 않도록 자기 저장 버전을 먼저 기록한다.
      restoredVersionIdRef.current = savedDraft.id;
      lastSavedContentRef.current = contentToSave;
      setLastSavedContent(contentToSave);
      setVersionId(savedDraft.id);
      setIsRevisionMode(false);
      setSaveState('saved');

      if (savedDraft.id !== versionId) {
        setSearchParams({ versionId: String(savedDraft.id) }, { replace: true });
      }

      return savedDraft.id;
    } catch {
      failedContentRef.current = contentToSave;
      setSaveState('error');
      return null;
    } finally {
      inFlightSaveRef.current = false;
    }
  }, [
    content,
    createDraft,
    createVersion,
    isRevisionMode,
    isSaving,
    matchedVersion,
    setSearchParams,
    updateDraft,
    versionId,
  ]);

  // 평가를 보낸 버전은 더 이상 DRAFT가 아니라 편집이 잠긴다. 그 버전과의 연결을 끊어
  // 다음 문장을 곧바로 쓸 수 있게 한다. URL의 versionId도 함께 지우지 않으면
  // 새로고침이 잠긴 버전을 다시 불러온다.
  const detachAfterEvaluation = useCallback(
    (evaluatedContent: string) => {
      // 결과를 기다리는 동안 이미 다음 문장을 쓰기 시작했다면 그 입력을 지우지 않는다.
      // 평가한 내용이 그대로 남아 있을 때만 비워 다음 문장 자리를 만든다.
      const keptContent = contentRef.current === evaluatedContent ? '' : contentRef.current;

      // 복원 기록은 지우지 않는다. 지우면 searchParams 변경이 반영되기 전 한 프레임 동안
      // 방금 떠난 버전을 복원 effect가 다시 집어넣어 비운 내용이 되살아난다.
      lastSavedContentRef.current = '';
      failedContentRef.current = null;
      setVersionId(null);
      setLastSavedContent('');
      setIsRevisionMode(false);
      setSaveState('idle');
      reset({ content: keptContent });
      setSearchParams(
        (prev) => {
          const next = new URLSearchParams(prev);
          next.delete('versionId');
          return next;
        },
        { replace: true },
      );
    },
    [reset, setSearchParams],
  );

  const reopenForRevision = useCallback(
    (targetVersionId: number) => {
      pendingRevisionVersionIdRef.current = targetVersionId;
      // 같은 버전을 이미 복원한 적이 있어도 다시 복원되도록 기록을 지운다.
      restoredVersionIdRef.current = null;
      setSearchParams(
        (prev) => {
          const next = new URLSearchParams(prev);
          next.set('versionId', String(targetVersionId));
          return next;
        },
        { replace: true },
      );
    },
    [setSearchParams],
  );

  // in-flight 중 pending으로 기록된 저장을 진행 중 저장이 끝난 뒤 실행한다.
  // effect는 재렌더 후 실행되므로 최신 content·versionId 기준의 saveDraft가
  // 호출되어, stale closure 재호출로 인한 이전 내용 저장이나 중복 생성이 없다.
  // (같은 내용이면 saveDraft 내부의 동일 내용 가드가 요청을 생략한다)
  useEffect(() => {
    if (hasPendingSave && !isSaving && !inFlightSaveRef.current) {
      setHasPendingSave(false);
      void saveDraft();
    }
  }, [hasPendingSave, isSaving, saveDraft]);

  // 저장 실패 후에도 사용자가 내용을 바꾸면 자동 저장을 다시 시도한다.
  useEffect(() => {
    if (saveState === 'error' && content !== failedContentRef.current) {
      failedContentRef.current = null;
      setSaveState('idle');
    }
  }, [content, saveState]);

  useEffect(() => {
    if (
      saveMode !== 'auto' ||
      hasInvalidVersionId ||
      sentenceVersionQuery.isLoading ||
      !isEditable ||
      !hasUnsavedContent ||
      isSaving ||
      saveState === 'error' ||
      errors.content
    ) {
      return;
    }

    const timerId = window.setTimeout(() => void saveDraft(), AUTO_SAVE_DELAY_MS);

    return () => window.clearTimeout(timerId);
  }, [
    content,
    errors.content,
    hasInvalidVersionId,
    hasUnsavedContent,
    isEditable,
    isSaving,
    saveDraft,
    saveMode,
    saveState,
    sentenceVersionQuery.isLoading,
  ]);

  // 내용을 전부 지운 상태도 서버에는 이전 문장이 남아 있으므로 이탈 확인 대상에 포함한다.
  useBeforeUnload(
    useCallback(
      (event) => {
        if (hasUnsavedChanges || saveState === 'error') {
          event.preventDefault();
        }
      },
      [hasUnsavedChanges, saveState],
    ),
  );

  const blocker = useBlocker(
    ({ currentLocation, nextLocation }) =>
      (hasUnsavedChanges || saveState === 'error') &&
      currentLocation.pathname !== nextLocation.pathname,
  );

  useEffect(() => {
    if (blocker.state !== 'blocked') {
      return;
    }

    if (window.confirm('저장되지 않은 내용이 있어요. 페이지를 이동할까요?')) {
      blocker.proceed();
    } else {
      blocker.reset();
    }
  }, [blocker]);

  const dailyWords = useMemo(
    () => dailyWordsQuery.data?.items ?? [],
    [dailyWordsQuery.data?.items],
  );
  const matchedWordIds = useMemo(
    () => getMatchedDailyWordIds(content, dailyWords),
    [content, dailyWords],
  );
  const matchedWords = dailyWords.filter(({ wordId }) => matchedWordIds.has(wordId));

  // 홈에서 개봉한 카드 상태를 이어받고, 이 화면의 개봉도 홈에 반영한다.
  const revealOwner = currentUserQuery.isSuccess
    ? (currentUserQuery.data?.id ?? ('guest' as const))
    : undefined;
  const { revealedWordIds, revealWord, revealWords } = useRevealedWords(
    revealOwner,
    dailyWordsQuery.data?.serviceDate,
  );

  // 우연히 미개봉 단어를 문장에 쓰면 그 순간 자동 개봉해
  // 화면 표시와 백엔드의 사용 단어 판정이 어긋나지 않게 한다.
  useEffect(() => {
    const hiddenMatchedIds = [...matchedWordIds].filter((wordId) => !revealedWordIds.has(wordId));

    if (hiddenMatchedIds.length > 0) {
      revealWords(hiddenMatchedIds);
    }
  }, [matchedWordIds, revealWords, revealedWordIds]);

  if (hasInvalidVersionId) {
    return (
      <ErrorState
        title="잘못된 초안 주소입니다"
        description="개인 문장 생성 화면에서 새 문장을 작성해 주세요."
      />
    );
  }

  if (dailyWordsQuery.isPending || sentenceVersionQuery.isLoading) {
    return (
      <LoadingState
        title="문장 생성 화면을 준비하고 있어요"
        description="오늘의 단어와 작성 중인 초안을 불러오고 있습니다."
      />
    );
  }

  if (dailyWordsQuery.isError || sentenceVersionQuery.isError) {
    return (
      <ErrorState
        title="문장 생성 화면을 불러오지 못했어요"
        description="잠시 후 다시 시도해 주세요."
        onRetry={() => {
          void dailyWordsQuery.refetch();
          void sentenceVersionQuery.refetch();
        }}
      />
    );
  }

  if (!isEditable && !renderActions) {
    return (
      <ErrorState
        title="수정할 수 없는 문장입니다"
        description="AI 평가가 시작되거나 완료된 문장은 초안으로 수정할 수 없어요."
      />
    );
  }

  const displayedSaveState =
    hasUnsavedChanges && saveState !== 'error' && !isSaving
      ? isContentEmpty
        ? 'empty'
        : 'waiting'
      : saveState;
  // 하위 컴포넌트(renderActions)에 내려줄 컨텍스트. 억제 주석의 범위를
  // 호출 한 줄로 좁히기 위해 객체 생성은 일반 린트 범위에 둔다.
  const actionsContext: SentenceDraftEditorContext = {
    versionId,
    content,
    dailyWords,
    matchedWordIds,
    hasUnsavedChanges,
    isSaving,
    isEditable,
    // versionId와 일치하는 버전만 내려 하위 컴포넌트의 판정 기준을 통일한다.
    sentenceVersion: matchedVersion,
    isRevisionMode,
    startRevision: () => setIsRevisionMode(true),
    cancelRevision: () => {
      // 수정 중이던 입력을 버리고 마지막 저장 내용으로 되돌린 뒤 수정 모드를 종료한다.
      reset({ content: lastSavedContent });
      setIsRevisionMode(false);
    },
    saveMode,
    saveDraft,
    detachAfterEvaluation,
    reopenForRevision,
  };

  const saveMessage = (
    saveMode === 'auto'
      ? {
          idle: '문장을 입력하면 자동으로 저장돼요.',
          waiting: '입력이 끝나면 곧 저장할게요.',
          empty: '내용을 입력해 주세요.',
          saving: '자동 저장 중…',
          saved: '자동 저장 완료',
          error: '자동 저장에 실패했어요.',
        }
      : {
          idle: '작성한 문장은 서고에 저장 버튼으로 보관할 수 있어요.',
          waiting: '저장하지 않은 변경이 있어요.',
          empty: '내용을 입력해 주세요.',
          saving: '서고에 저장 중…',
          saved: '서고에 저장 완료',
          error: '서고에 저장하지 못했어요.',
        }
  )[displayedSaveState];

  return (
    <div className="space-y-10 lg:space-y-12">
      {/* 제목은 전 페이지 공통 스타일(text-violet-800)을 따르고, 저장 상태는 제목 아래 한 단으로 둔다. */}
      <header>
        <h1 className="text-2xl font-extrabold text-violet-800">{title}</h1>
        <p className="mt-2 text-sm leading-6 text-stone-600">{description}</p>

        {saveMode === 'manual' && (
          <div className="mt-2 flex items-center gap-3">
            <p role="status" aria-live="polite" className="text-xs font-medium text-stone-500">
              {saveMessage}
            </p>
            {saveState === 'error' && (
              <button
                type="button"
                onClick={() => void saveDraft()}
                className="shrink-0 text-xs font-semibold text-red-700 underline underline-offset-2"
              >
                다시 저장
              </button>
            )}
          </div>
        )}
      </header>

      <section
        aria-labelledby="word-pack-title"
        aria-describedby={saveMode === 'manual' ? 'word-match-hint' : undefined}
        className={
          saveMode === 'auto'
            ? 'rounded-2xl border border-stone-200 bg-stone-50/60 px-4 py-5 sm:px-5'
            : undefined
        }
        data-variant={saveMode === 'auto' ? 'card' : 'plain'}
      >
        <div className="mb-3 flex items-center justify-between gap-3">
          <h2 id="word-pack-title" className="text-sm font-bold text-stone-900">
            오늘의 단어팩
          </h2>
          <span className="text-xs font-semibold text-violet-700">
            {matchedWordIds.size} / {dailyWords.length}개 사용
          </span>
        </div>

        <DraftWordPack
          items={dailyWords}
          matchedWordIds={matchedWordIds}
          revealedWordIds={revealedWordIds}
          onReveal={revealWord}
        />

        {saveMode === 'manual' && (
          <p id="word-match-hint" className="mt-3 text-xs leading-5 text-stone-500">
            동사·형용사는 시제나 형태가 바뀌면 사용 표시가 되지 않을 수 있어요.
            <br />
            실제 사용 여부는 AI 평가에서 정확하게 반영됩니다.
          </p>
        )}
      </section>

      <section aria-labelledby="writing-title">
        <div className="mb-2 flex items-center justify-between gap-3">
          <h2 id="writing-title" className="text-sm font-bold text-stone-900">
            {saveMode === 'auto' ? '나의 글' : '글쓰기'}
          </h2>
          {/* 수동 저장 모드에서는 뱃지를 노출하지 않고, 자동 저장 모드에서만 상태를 표시한다. */}
          {saveMode === 'auto' && (
            <span className="rounded-full bg-violet-50 px-2.5 py-1 text-xs font-medium text-violet-700">
              자동 저장
            </span>
          )}
        </div>

        <label htmlFor="sentence-content" className="sr-only">
          작성할 문장
        </label>
        <textarea
          id="sentence-content"
          rows={9}
          maxLength={MAX_CONTENT_LENGTH}
          placeholder={
            saveMode === 'auto'
              ? '오늘의 단어로 이야기를 시작해 보세요.'
              : '오늘의 단어를 활용해 나만의 문장을 작성해 보세요.'
          }
          aria-describedby={saveMode === 'manual' ? 'sentence-content-help' : undefined}
          aria-invalid={errors.content ? true : undefined}
          aria-errormessage={errors.content ? 'sentence-content-error' : undefined}
          disabled={!isEditable}
          className="w-full resize-none rounded-2xl border border-stone-200 bg-white px-4 py-4 text-base leading-7 text-stone-900 shadow-sm outline-none transition placeholder:text-stone-400 focus:border-violet-400 focus:ring-4 focus:ring-violet-100 lg:min-h-80 lg:px-6 lg:py-5"
          {...register('content')}
        />

        <div className="mt-2 flex items-start justify-between gap-3 text-xs">
          <div className="flex items-center gap-2 text-stone-500">
            {saveMode === 'auto' ? (
              <>
                <p role="status" aria-live="polite">
                  {saveMessage}
                </p>
                {saveState === 'error' && (
                  <button
                    type="button"
                    onClick={() => void saveDraft()}
                    className="font-semibold text-red-700 underline underline-offset-2"
                  >
                    다시 저장
                  </button>
                )}
              </>
            ) : (
              <p id="sentence-content-help">
                {matchedWords.length > 0
                  ? `${matchedWords.map(({ word }) => word).join(', ')} 사용됨`
                  : '오늘의 단어를 문장에 넣어 보세요.'}
              </p>
            )}
          </div>
          <span
            className={content.length >= 450 ? 'font-semibold text-orange-700' : 'text-stone-500'}
          >
            {content.length}자 / {MAX_CONTENT_LENGTH}자
          </span>
        </div>

        {errors.content && (
          <p id="sentence-content-error" role="alert" className="mt-2 text-sm text-red-700">
            {errors.content.message}
          </p>
        )}

        {/* 자동 저장을 기다리지 않고 바로 보관하고 싶을 때 쓰는 즉시 저장 버튼.
            저장된 상태(자동 저장 포함)에서는 저장 완료로 표시한다. */}
        {saveMode === 'auto' && (
          <Button
            type="button"
            className="mt-4 w-full"
            disabled={!isEditable || isSaving || isContentEmpty || !hasUnsavedChanges}
            onClick={() => void saveDraft()}
          >
            {isSaving
              ? '저장 중…'
              : saveState === 'saved' && !hasUnsavedChanges && !isContentEmpty
                ? '저장 완료'
                : '저장'}
          </Button>
        )}
      </section>

      {content && saveMode === 'manual' && (
        <section
          aria-labelledby="matched-preview-title"
          className="rounded-2xl border border-violet-100 bg-violet-50/70 p-4"
        >
          <h2 id="matched-preview-title" className="mb-2 text-xs font-bold text-violet-800">
            사용 단어 미리보기
          </h2>
          <MatchedSentencePreview
            content={content}
            dailyWords={dailyWords}
            matchedWordIds={matchedWordIds}
          />
        </section>
      )}

      {renderActions && (
        <>
          {/* saveDraft는 하위 컴포넌트의 이벤트 핸들러에서만 호출되고 렌더 중에는
              실행되지 않으므로, 렌더 중 ref 접근 경고는 이 호출에 한해 오탐이다. */}
          {/* eslint-disable-next-line react-hooks/refs */}
          {renderActions(actionsContext)}
        </>
      )}
    </div>
  );
}
