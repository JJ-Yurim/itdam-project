const WEEK_ORDINALS = ['첫째', '둘째', '셋째', '넷째', '다섯째'];

// 주 시작일(YYYY-MM-DD)을 "8월 첫째 주" 형식으로 표시한다.
// 몇째 주인지는 시작일이 그 달에서 몇 번째 7일 구간에 있는지로 계산한다.
// Date 파싱은 타임존에 따라 날짜가 하루 밀릴 수 있어 문자열을 직접 읽는다.
export function formatWeekLabel(weekStartDate: string): string {
  const matched = /^(\d{4})-(\d{2})-(\d{2})$/.exec(weekStartDate);

  if (!matched) {
    return '이번 주';
  }

  const month = Number(matched[2]);
  const day = Number(matched[3]);
  const ordinal = WEEK_ORDINALS[Math.min(Math.ceil(day / 7), WEEK_ORDINALS.length) - 1];

  return `${month}월 ${ordinal} 주`;
}
