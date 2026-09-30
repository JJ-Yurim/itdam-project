/**
 * 관리 표의 시각 표기. 한 줄에 여러 시각이 들어가므로 공용 formatDateTime(연·월 이름 포함)보다
 * 짧은 형식을 쓴다. Intl은 잘못된 Date에 RangeError를 던지고, 그러면 목록이 렌더 중에 죽어
 * 오류 화면조차 보여 주지 못하므로 값을 먼저 확인한다.
 */
const adminDateTimeFormatter = new Intl.DateTimeFormat('ko-KR', {
  dateStyle: 'short',
  timeStyle: 'short',
});

export function formatAdminDateTime(value: string | null | undefined): string {
  const date = value ? new Date(value) : null;
  return date && !Number.isNaN(date.getTime()) ? adminDateTimeFormatter.format(date) : '-';
}
