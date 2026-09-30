// 알림 설정은 아직 백엔드 API가 없어 localStorage에만 저장한다.
// 이후 API가 생기면 이 모듈의 load/save 구현만 서버 연동으로 교체한다.

export interface NotificationSettings {
  evaluationCompleted: boolean;
  raidEnded: boolean;
  sentenceIncluded: boolean;
  personalBookReview: boolean;
  bestSentence: boolean;
  commentsAndReactions: boolean;
}

export const DEFAULT_NOTIFICATION_SETTINGS: NotificationSettings = {
  evaluationCompleted: true,
  raidEnded: true,
  sentenceIncluded: true,
  personalBookReview: true,
  bestSentence: true,
  commentsAndReactions: true,
};

// 같은 브라우저에서 계정을 바꿔 로그인해도 설정이 섞이지 않게 userId를 키에 포함한다.
function storageKey(userId: number): string {
  return `notification-settings:${userId}`;
}

export function loadNotificationSettings(userId: number): NotificationSettings {
  try {
    const raw = localStorage.getItem(storageKey(userId));

    if (!raw) {
      return DEFAULT_NOTIFICATION_SETTINGS;
    }

    const parsed: unknown = JSON.parse(raw);

    if (typeof parsed !== 'object' || parsed === null) {
      return DEFAULT_NOTIFICATION_SETTINGS;
    }

    // 저장된 값에 없는 항목(이후 추가된 알림 종류)은 기본값으로 채운다.
    return { ...DEFAULT_NOTIFICATION_SETTINGS, ...(parsed as Partial<NotificationSettings>) };
  } catch {
    return DEFAULT_NOTIFICATION_SETTINGS;
  }
}

export function saveNotificationSettings(userId: number, settings: NotificationSettings): void {
  try {
    localStorage.setItem(storageKey(userId), JSON.stringify(settings));
  } catch {
    // 저장 실패(용량 초과 등)는 치명적이지 않으므로 무시한다.
  }
}
