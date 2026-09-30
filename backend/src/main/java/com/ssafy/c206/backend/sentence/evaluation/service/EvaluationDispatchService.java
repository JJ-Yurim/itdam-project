package com.ssafy.c206.backend.sentence.evaluation.service;

import com.ssafy.c206.backend.integration.evaluation.SentenceEvaluationRequestMessage;
import com.ssafy.c206.backend.integration.evaluation.SentenceEvaluationRequester;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * 접수된 평가를 브로커에 발행한다(ISSUE-208).
 *
 * <p>이 빈의 존재 자체가 모드 스위치다. {@code evaluation.dispatch=rabbit}일 때만 등록되고,
 * 접수 경로는 ObjectProvider로 존재 여부를 보고 비동기/동기를 가른다.
 *
 * <p>발행은 어떤 트랜잭션에도 속하지 않는다. 준비(읽기)와 마킹(쓰기)이 앞뒤의 짧은
 * 트랜잭션이고, confirm 대기(최대 5초)는 그 사이 맨땅에서 일어난다.
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "evaluation.dispatch", havingValue = "rabbit")
@RequiredArgsConstructor
public class EvaluationDispatchService {

	private final EvaluationDispatchTransaction dispatchTransaction;
	private final SentenceEvaluationRequester requester;

	/**
	 * 접수 직후의 즉시 발행. 실패해도 접수 응답(202 성격의 EVALUATING)은 이미 확정이므로
	 * 삼키고 로그만 남긴다 — 미발행 행은 릴레이가 집어 다시 발행한다.
	 */
	public void dispatchAfterAccept(long versionId) {
		try {
			dispatch(versionId);
		} catch (RuntimeException exception) {
			log.warn("sentence_evaluation_dispatch_deferred version_id={} — relay will retry", versionId, exception);
		}
	}

	/** 릴레이가 재사용하는 발행 단위. 대상이 아니면 아무것도 하지 않는다. */
	public void dispatch(long versionId) {
		SentenceEvaluationRequestMessage message = dispatchTransaction.prepare(versionId);
		if (message == null) {
			return;
		}
		requester.request(message);
		dispatchTransaction.markDispatched(versionId, message.correlationId());
	}
}
