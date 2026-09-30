export const TUTORIAL_STEP_LABELS = [
  '시작',
  '단어',
  '문장',
  '평가',
  '원정',
  '책 상세',
  '전체 읽기',
  '완료',
] as const;

/** 단계 라벨을 단일 원본으로 삼아 안내 문구·진행률·리듀서의 범위가 함께 바뀌게 한다. */
export const TUTORIAL_STEP_COUNT = TUTORIAL_STEP_LABELS.length;
