import { useQuery } from '@tanstack/react-query';

import { getDailyWordsPreview, getTodayDailyWords } from './api';

export const dailyWordKeys = {
  all: ['daily-words'] as const,
  today: () => [...dailyWordKeys.all, 'today'] as const,
  preview: () => [...dailyWordKeys.all, 'preview'] as const,
};

export function useTodayDailyWordsQuery(options: { enabled?: boolean } = {}) {
  const { enabled = true } = options;

  return useQuery({
    queryKey: dailyWordKeys.today(),
    queryFn: getTodayDailyWords,
    staleTime: 5 * 60 * 1000,
    enabled,
  });
}

// 비회원용 오늘의 단어 미리보기. 회원 세트와 달리 문장 작성에는 사용할 수 없다.
export function useDailyWordsPreviewQuery(options: { enabled?: boolean } = {}) {
  const { enabled = true } = options;

  return useQuery({
    queryKey: dailyWordKeys.preview(),
    queryFn: getDailyWordsPreview,
    staleTime: 5 * 60 * 1000,
    enabled,
  });
}
