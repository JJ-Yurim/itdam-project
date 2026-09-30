export interface DailyWord {
  position: number;
  wordId: number;
  word: string;
  partOfSpeech: string;
  definition: string;
  example: string;
}

export interface DailyWordResponse {
  dailyWordSetId: number;
  serviceDate: string;
  items: DailyWord[];
}

// 비회원용 미리보기 응답. 지급 이력이 없어 dailyWordSetId가 없는 열람 전용 데이터다.
export interface DailyWordPreviewResponse {
  serviceDate: string;
  items: DailyWord[];
}
