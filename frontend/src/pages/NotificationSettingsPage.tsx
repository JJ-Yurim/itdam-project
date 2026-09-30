import { useState } from 'react';

import { Link } from 'react-router-dom';

import { useCurrentUserQuery } from '@/features/auth';
import {
  loadNotificationSettings,
  saveNotificationSettings,
} from '@/features/settings';
import { ErrorState, LoadingState } from '@/shared/components';

import type { NotificationSettings } from '@/features/settings';

const NOTIFICATION_ITEMS: { key: keyof NotificationSettings; label: string }[] = [
  { key: 'evaluationCompleted', label: 'AI 문장 평가 완료 알림' },
  { key: 'raidEnded', label: '원정 종료 알림' },
  { key: 'sentenceIncluded', label: '내 문장 공동 책 수록 알림' },
  { key: 'personalBookReview', label: '개인 책 공개 심사 결과 알림' },
  { key: 'bestSentence', label: '이주의 문장 선정 알림' },
  { key: 'commentsAndReactions', label: '댓글 및 반응 알림' },
];

function NotificationToggleList({ userId }: { userId: number }) {
  const [settings, setSettings] = useState<NotificationSettings>(() =>
    loadNotificationSettings(userId),
  );

  const toggle = (key: keyof NotificationSettings) => {
    setSettings((current) => {
      const next = { ...current, [key]: !current[key] };
      saveNotificationSettings(userId, next);
      return next;
    });
  };

  return (
    <ul className="space-y-2">
      {NOTIFICATION_ITEMS.map(({ key, label }) => (
        <li
          key={key}
          className="flex min-h-14 items-center justify-between gap-3 rounded-xl border border-stone-200 bg-white px-4"
        >
          <span className="text-sm font-semibold text-stone-800">{label}</span>

          <button
            type="button"
            role="switch"
            aria-checked={settings[key]}
            aria-label={label}
            onClick={() => toggle(key)}
            className={[
              'relative h-6 w-11 shrink-0 rounded-full transition-colors focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-violet-600',
              settings[key] ? 'bg-violet-500' : 'bg-stone-300',
            ].join(' ')}
          >
            <span
              aria-hidden="true"
              className={[
                'absolute top-0.5 h-5 w-5 rounded-full bg-white shadow transition-transform',
                settings[key] ? 'translate-x-[1.375rem]' : 'translate-x-0.5',
              ].join(' ')}
              style={{ left: 0 }}
            />
          </button>
        </li>
      ))}
    </ul>
  );
}

export function NotificationSettingsPage() {
  const currentUserQuery = useCurrentUserQuery();

  return (
    <section className="space-y-6">
      <header>
        <Link
          to="/my-page"
          className="text-sm font-semibold text-stone-500 transition hover:text-stone-800"
        >
          ‹ 마이페이지
        </Link>
        <h1 className="mt-2 text-2xl font-extrabold text-stone-900">알림 설정</h1>
        <p className="mt-2 text-sm text-stone-600">
          받고 싶은 알림을 선택하세요. 설정은 이 기기에 저장돼요.
        </p>
      </header>

      {currentUserQuery.isPending && (
        <LoadingState title="설정을 불러오고 있어요" description="잠시만 기다려 주세요." />
      )}

      {currentUserQuery.isError && (
        <ErrorState
          title="설정을 불러오지 못했어요"
          description="잠시 후 다시 시도해 주세요."
          onRetry={() => void currentUserQuery.refetch()}
        />
      )}

      {currentUserQuery.isSuccess && !currentUserQuery.data && (
        <div className="py-12 text-center">
          <p className="font-semibold text-stone-700">로그인하면 알림을 설정할 수 있어요</p>
          <Link
            to="/login"
            className="mt-4 inline-flex min-h-11 items-center justify-center rounded-xl bg-violet-600 px-4 text-sm font-semibold text-white transition hover:bg-violet-700"
          >
            로그인 하기
          </Link>
        </div>
      )}

      {currentUserQuery.isSuccess && currentUserQuery.data && (
        <NotificationToggleList userId={currentUserQuery.data.id} />
      )}
    </section>
  );
}
