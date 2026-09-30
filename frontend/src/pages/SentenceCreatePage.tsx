import { SentenceDraftEditor, SentenceEvaluationPanel } from '@/features/sentences';

export function SentenceCreatePage() {
  return (
    <SentenceDraftEditor
      title="오늘의 문장 만들기"
      description="오늘의 단어를 활용해 최대 500자의 짧은 글을 작성해 보세요. AI 평가를 요청하면 문장이 서고에 함께 저장돼요."
      saveMode="manual"
      renderActions={(context) => <SentenceEvaluationPanel {...context} />}
    />
  );
}
