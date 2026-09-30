package com.ssafy.c206.backend.integration.evaluation;

/** 평가 요청 발행 경계. 전송 세부(브로커·confirm)를 도메인 서비스에서 격리한다. */
public interface SentenceEvaluationRequester {

	void request(SentenceEvaluationRequestMessage message);
}
