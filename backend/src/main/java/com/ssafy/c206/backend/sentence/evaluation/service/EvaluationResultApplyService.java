package com.ssafy.c206.backend.sentence.evaluation.service;

import com.ssafy.c206.backend.integration.ai.AiEvaluationInvalidResponseException;
import com.ssafy.c206.backend.integration.ai.AiEvaluationResultValidator;
import com.ssafy.c206.backend.integration.ai.SafetyStatus;
import com.ssafy.c206.backend.integration.evaluation.SentenceEvaluationMqContract;
import com.ssafy.c206.backend.integration.evaluation.SentenceEvaluationResultMessage;
import com.ssafy.c206.backend.sentence.domain.EvaluationStatus;
import com.ssafy.c206.backend.sentence.domain.SentenceVersion;
import com.ssafy.c206.backend.sentence.repository.SentenceVersionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * ai-service가 보낸 평가 결과를 문장 버전에 반영한다(ISSUE-208).
 *
 * <p>멱등성 규칙: 결과는 (a) 버전이 아직 EVALUATING이고 (b) 상관 ID가 현재 시도의 것과
 * 일치할 때만 적용된다. 중복 전달·재발행으로 같은 결과가 두 번 와도 두 번째는 (a)에서
 * 떨어지고, stale 회수 후 재시도가 시작된 뒤 옛 시도의 결과가 늦게 도착하면 (b)에서
 * 떨어진다. 두 경우 모두 정상 시나리오라 조용히 버린다(ack).
 *
 * <p>예외를 던지는 경우는 "재시도하면 나아질 수 있는" 것뿐이다: 계약 버전 불일치(배포
 * 어긋남 — DLQ에서 보존), DB 순단. 반대로 결과 페이로드가 검증에 실패하면 재시도해도
 * 같은 페이로드라 나아질 게 없고 사용자는 폴링으로 기다리는 중이므로, 책 파이프라인의
 * DLQ 보존 정책과 달리 즉시 실패 처리(사용량 반환 포함)로 종결한다.
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "evaluation.dispatch", havingValue = "rabbit")
@RequiredArgsConstructor
public class EvaluationResultApplyService {

	private final SentenceVersionRepository versionRepository;
	private final EvaluationDispatchTransaction dispatchTransaction;
	private final EvaluationCompletionTransaction completionTransaction;

	@Transactional
	public void apply(SentenceEvaluationResultMessage message) {
		if (!SentenceEvaluationMqContract.RESULT_CONTRACT_VERSION.equals(message.contractVersion())) {
			throw new IllegalStateException("Unsupported evaluation result contract: " + message.contractVersion());
		}
		SentenceVersion version = versionRepository
				.findWithSentenceByIdForUpdate(message.sentenceVersionId()).orElse(null);
		if (version == null) {
			log.warn("sentence_evaluation_result_dropped reason=version_missing version_id={}",
					message.sentenceVersionId());
			return;
		}
		if (!message.correlationId().equals(version.getEvaluationCorrelationId())) {
			log.info("sentence_evaluation_result_dropped reason=stale_correlation version_id={} correlation_id={}",
					message.sentenceVersionId(), message.correlationId());
			return;
		}
		if (version.getEvaluationStatus() != EvaluationStatus.EVALUATING) {
			log.info("sentence_evaluation_result_dropped reason=already_terminal version_id={} status={}",
					message.sentenceVersionId(), version.getEvaluationStatus());
			return;
		}

		EvaluationAttempt attempt = dispatchTransaction.reconstructAttempt(version);
		if (!message.success()) {
			log.warn("sentence_evaluation_failed_remotely version_id={} error_code={} error_message={}",
					message.sentenceVersionId(), message.errorCode(), message.errorMessage());
			completionTransaction.completeFailure(attempt);
			return;
		}
		try {
			AiEvaluationResultValidator.validate(attempt.command(), message.evaluation());
		} catch (AiEvaluationInvalidResponseException exception) {
			log.error("sentence_evaluation_result_invalid version_id={}", message.sentenceVersionId(), exception);
			completionTransaction.completeFailure(attempt);
			return;
		}
		if (message.evaluation().safety().status() == SafetyStatus.REJECTED) {
			completionTransaction.completeRejected(attempt);
			return;
		}
		completionTransaction.completeSuccess(attempt, message.evaluation());
	}
}
