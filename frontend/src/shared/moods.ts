export const moodLabels = {
  JOY: '기쁨',
  HOPE: '희망',
  LOVE: '사랑',
  CALM: '평온',
  NOSTALGIA: '그리움',
  SADNESS: '슬픔',
  LONELINESS: '외로움',
  ANGER: '분노',
  TENSION: '긴장',
  MYSTERY: '신비',
} as const;

export type MoodCode = keyof typeof moodLabels;

export const moodCodes = Object.keys(moodLabels) as MoodCode[];

export function getMoodLabel(code: string) {
  return moodLabels[code as MoodCode] ?? code;
}
