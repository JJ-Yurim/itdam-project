import { httpClient } from '@/shared/api';

import type { DailyWord, DailyWordPreviewResponse, DailyWordResponse } from './types';

function isDailyWordItem(value: unknown): value is DailyWord {
  if (!value || typeof value !== 'object') {
    return false;
  }

  const item = value as Record<string, unknown>;

  return (
    typeof item.position === 'number' &&
    typeof item.wordId === 'number' &&
    typeof item.word === 'string'
  );
}

// today·preview 응답의 공통 부분(serviceDate·items)을 한 곳에서 검증·정렬한다.
function parseDailyWords(
  data: unknown,
  context: string,
): { serviceDate: string; items: DailyWord[] } {
  if (!data || typeof data !== 'object') {
    throw new Error(`${context} response is invalid.`);
  }

  const { serviceDate, items: rawItems } = data as Record<string, unknown>;

  if (typeof serviceDate !== 'string') {
    throw new Error(`${context} response is invalid.`);
  }

  // position이 없거나 형식이 다른 항목은 정렬을 깨뜨리므로 제외한다.
  const items = Array.isArray(rawItems) ? rawItems.filter(isDailyWordItem) : [];

  return {
    serviceDate,
    items: items.sort((first, second) => first.position - second.position),
  };
}

export async function getTodayDailyWords(): Promise<DailyWordResponse> {
  const { data } = await httpClient.get<unknown>('/v1/daily-words/today');
  const { serviceDate, items } = parseDailyWords(data, 'Today daily words');
  const { dailyWordSetId } = data as Record<string, unknown>;

  if (typeof dailyWordSetId !== 'number') {
    throw new Error('Today daily words response is invalid.');
  }

  return { dailyWordSetId, serviceDate, items };
}

export async function getDailyWordsPreview(): Promise<DailyWordPreviewResponse> {
  const { data } = await httpClient.get<unknown>('/v1/daily-words/preview');

  return parseDailyWords(data, 'Daily words preview');
}
