package com.ssafy.c206.backend.integration.book;

/**
 * book.generation.request 큐에 싣는 요청 페이로드.
 *
 * <p>재료(제출 문장·보스 테마)는 싣지 않는다(ADR-007). ai-service 소비자가 jobId로
 * DB에서 직접 읽는다(ISSUE-122). correlationId는 job 행의 값과 같아야 하며,
 * 결과 적용 시 대조되어 엉뚱한 결과가 반영되는 것을 막는다.
 */
public record BookGenerationRequestMessage(String contractVersion, String correlationId, Long jobId) {
}
