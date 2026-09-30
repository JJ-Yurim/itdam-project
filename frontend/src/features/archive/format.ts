import { getMoodLabel } from '@/shared/moods';

export function getMoodName(code: string) {
  return getMoodLabel(code);
}

export function formatArchiveDate(value: string) {
  return new Intl.DateTimeFormat('ko-KR', { dateStyle: 'medium' }).format(new Date(value));
}
