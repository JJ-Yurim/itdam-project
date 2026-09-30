import { describe, expect, it } from 'vitest';

import { deleteArchiveSentence, getArchive, getArchiveDetail, getArchiveEvaluation } from './api';

describe('archive API', () => {
  it('페이지 기준으로 내 문장 목록을 조회한다', async () => {
    const result = await getArchive(0, 10);

    expect(result.content).toHaveLength(2);
    expect(result.totalElements).toBe(2);
  });

  it('문장 상세와 세부 평가 결과를 조회한다', async () => {
    const [detail, evaluation] = await Promise.all([
      getArchiveDetail(84),
      getArchiveEvaluation(84),
    ]);

    expect(detail.sentenceVersionId).toBe(84);
    expect(detail.bossSubmissions).toHaveLength(1);
    expect(evaluation.scores?.baseScore).toBe(83.35);
  });

  it('문장을 삭제한다', async () => {
    await expect(deleteArchiveSentence(42)).resolves.toBeUndefined();
  });
});
