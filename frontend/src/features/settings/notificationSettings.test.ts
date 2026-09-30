import { beforeEach, describe, expect, it } from 'vitest';

import {
  DEFAULT_NOTIFICATION_SETTINGS,
  loadNotificationSettings,
  saveNotificationSettings,
} from './notificationSettings';

describe('notificationSettings', () => {
  beforeEach(() => {
    localStorage.clear();
  });

  it('저장된 값이 없으면 기본값(전체 켜짐)을 반환한다', () => {
    expect(loadNotificationSettings(1)).toEqual(DEFAULT_NOTIFICATION_SETTINGS);
  });

  it('저장한 설정을 같은 사용자 키로 다시 불러온다', () => {
    saveNotificationSettings(1, { ...DEFAULT_NOTIFICATION_SETTINGS, bestSentence: false });

    expect(loadNotificationSettings(1).bestSentence).toBe(false);
  });

  it('사용자가 다르면 설정이 분리된다', () => {
    saveNotificationSettings(1, { ...DEFAULT_NOTIFICATION_SETTINGS, raidEnded: false });

    expect(loadNotificationSettings(2).raidEnded).toBe(true);
  });

  it('저장 값이 깨져 있으면 기본값으로 복구한다', () => {
    localStorage.setItem('notification-settings:1', '{broken json');

    expect(loadNotificationSettings(1)).toEqual(DEFAULT_NOTIFICATION_SETTINGS);
  });

  it('저장 값에 없는 새 항목은 기본값으로 채운다', () => {
    localStorage.setItem('notification-settings:1', JSON.stringify({ raidEnded: false }));

    const settings = loadNotificationSettings(1);

    expect(settings.raidEnded).toBe(false);
    expect(settings.evaluationCompleted).toBe(true);
  });
});
