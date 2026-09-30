// 포맷터 인스턴스는 생성 비용이 있어 모듈 레벨에 캐싱해 재사용한다.
const dateTimeFormatter = new Intl.DateTimeFormat('ko-KR', {
  year: 'numeric',
  month: 'long',
  day: 'numeric',
  hour: 'numeric',
  minute: 'numeric',
});

// 이력성 데이터는 해를 넘길 수 있으므로 연도를 포함해 표시한다.
// 파싱할 수 없는 값은 예외 대신 대체 문자를 반환해 목록 렌더링이 깨지지 않게 한다.
export function formatDateTime(value: string | Date): string {
  const date = new Date(value);

  if (Number.isNaN(date.getTime())) {
    return '-';
  }

  return dateTimeFormatter.format(date);
}
