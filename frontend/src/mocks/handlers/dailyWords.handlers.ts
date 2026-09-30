import { HttpResponse, http } from 'msw';

import type { DailyWordPreviewResponse, DailyWordResponse } from '@/features/daily-words';

const todayDailyWords: DailyWordResponse = {
  dailyWordSetId: 12,
  serviceDate: '2026-07-30',
  items: [
    {
      position: 1,
      wordId: 101,
      word: '은하수',
      partOfSpeech: '명사',
      definition: '맑은 밤하늘에 강처럼 보이는 수많은 별의 무리',
      example: '여름밤 은하수가 우리 머리 위로 길게 흘렀다.',
    },
    {
      position: 2,
      wordId: 102,
      word: '산들바람',
      partOfSpeech: '명사',
      definition: '시원하고 가볍게 부는 바람',
      example: '산들바람이 열린 창문으로 조용히 들어왔다.',
    },
    {
      position: 3,
      wordId: 103,
      word: '윤슬',
      partOfSpeech: '명사',
      definition: '햇빛이나 달빛에 비치어 반짝이는 잔물결',
      example: '호수 위 윤슬이 보석처럼 반짝였다.',
    },
    {
      position: 4,
      wordId: 104,
      word: '포근하다',
      partOfSpeech: '형용사',
      definition: '감싸 주는 것처럼 따뜻하고 편안하다',
      example: '햇살이 비치는 방 안은 포근했다.',
    },
    {
      position: 5,
      wordId: 105,
      word: '마중',
      partOfSpeech: '명사',
      definition: '오는 사람을 나가서 맞이하는 일',
      example: '강아지가 꼬리를 흔들며 나를 마중 나왔다.',
    },
  ],
};

// 비회원 미리보기: 같은 날짜에는 회원 세트와 같은 단어를 제공하되 dailyWordSetId가 없다.
const dailyWordsPreview: DailyWordPreviewResponse = {
  serviceDate: todayDailyWords.serviceDate,
  items: todayDailyWords.items,
};

export const dailyWordsHandlers = [
  http.get('*/api/v1/daily-words/today', () => HttpResponse.json(todayDailyWords)),
  http.get('*/api/v1/daily-words/preview', () => HttpResponse.json(dailyWordsPreview)),
];
