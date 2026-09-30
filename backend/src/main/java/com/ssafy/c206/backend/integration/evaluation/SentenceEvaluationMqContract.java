package com.ssafy.c206.backend.integration.evaluation;

/**
 * 문장 평가 비동기 파이프라인의 MQ 계약(ISSUE-208).
 *
 * <p>책 생성과 달리 요청 메시지에 평가 재료(문장 + 오늘의 단어 5장)를 그대로 싣는다.
 * 재료가 1~2KB라 ADR-007이 막는 대용량이 아니고, 이렇게 하면 ai-service가 DB에 접근할
 * 필요가 없어 SELECT 전용 계정의 권한을 넓히지 않아도 된다. EVALUATING으로 전이된
 * 버전은 수정이 막혀 있어 스냅샷과 처리 시점 내용이 항상 같다.
 *
 * <p>요청 본문의 계약 버전은 HTTP 경로와 동일한 값을 쓴다. ai-service가 두 경로 모두
 * 같은 {@code EvaluationRequest} 스키마로 검증하기 때문이다.
 */
public final class SentenceEvaluationMqContract {

	public static final String EXCHANGE = "sentence.evaluation";
	public static final String REQUEST_QUEUE = "sentence.evaluation.request";
	public static final String REQUEST_DLQ = "sentence.evaluation.request.dlq";
	public static final String RESULT_QUEUE = "sentence.evaluation.result";
	public static final String RESULT_DLQ = "sentence.evaluation.result.dlq";

	/** HTTP 평가 계약과 동일해야 한다. ai-service의 EvaluationRequest가 리터럴로 검증한다. */
	public static final String REQUEST_CONTRACT_VERSION = "sentence-evaluation-v1";
	public static final String RESULT_CONTRACT_VERSION = "sentence-evaluation-mq-v1";

	private SentenceEvaluationMqContract() {
	}
}
