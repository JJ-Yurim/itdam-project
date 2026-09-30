package com.ssafy.c206.backend.integration.book;

/**
 * 책 생성 MQ 계약(ISSUE-95).
 *
 * <p>ai-service 소비자(ISSUE-122)와 공유하는 값이므로 여기를 바꾸면 양쪽을
 * 함께 배포해야 한다. 요청 페이로드에는 재료를 싣지 않는다(ADR-007) — 소비자가
 * jobId로 DB에서 직접 읽는다. 결과 페이로드는 ai-service의 BookGenerationResponse
 * 스키마를 성공/실패 envelope로 감싼 형태다.
 *
 * <p>MAX_SUBMISSIONS는 ai-service 스키마의 상한(60)과 같아야 하고, 초과 시 선별
 * 규칙은 "finalDamage 내림차순, id 오름차순"으로 양쪽이 동일해야 한다. Spring의
 * 결과 검증(validateSubmissionCoverage)이 같은 규칙으로 기대 집합을 재계산하므로,
 * 어느 한쪽만 바꾸면 정상 결과가 계약 위반으로 거부된다.
 */
public final class BookGenerationMqContract {

	public static final String REQUEST_CONTRACT_VERSION = "book-generation-v1";
	public static final String RESULT_CONTRACT_VERSION = "book-generation-mq-v1";
	public static final int MAX_SUBMISSIONS = 60;

	public static final String EXCHANGE = "book.generation";
	public static final String REQUEST_QUEUE = "book.generation.request";
	public static final String REQUEST_DLQ = "book.generation.request.dlq";
	public static final String RESULT_QUEUE = "book.generation.result";
	public static final String RESULT_DLQ = "book.generation.result.dlq";

	private BookGenerationMqContract() {
	}
}
