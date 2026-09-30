package com.ssafy.c206.backend.sentence.evaluation.service;

import java.time.LocalDate;

import com.ssafy.c206.backend.common.api.DomainException;
import com.ssafy.c206.backend.common.api.ErrorCode;
import com.ssafy.c206.backend.common.time.ServiceClock;
import com.ssafy.c206.backend.dailyword.service.DailyWordService;
import com.ssafy.c206.backend.integration.ai.AiEvaluationClient;
import com.ssafy.c206.backend.integration.ai.AiEvaluationResult;
import com.ssafy.c206.backend.integration.ai.SafetyStatus;
import com.ssafy.c206.backend.sentence.evaluation.api.EvaluationUsageResponse;
import com.ssafy.c206.backend.sentence.evaluation.api.SentenceEvaluationResponse;
import com.ssafy.c206.backend.sentence.evaluation.repository.SentenceEvaluationRepository;
import com.ssafy.c206.backend.sentence.evaluation.repository.SentenceMoodRepository;
import com.ssafy.c206.backend.sentence.evaluation.repository.SentenceUsedDailyWordRepository;
import com.ssafy.c206.backend.sentence.evaluation.repository.UserDailyEvaluationUsageRepository;
import com.ssafy.c206.backend.sentence.repository.SentenceVersionRepository;
import com.ssafy.c206.backend.common.security.AuthenticatedUser;
import com.ssafy.c206.backend.common.security.AuthenticatedUsers;
import com.ssafy.c206.backend.sentence.domain.EvaluationStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class SentenceEvaluationService {

	private final DailyWordService dailyWordService;
	private final EvaluationStartTransaction startTransaction;
	private final EvaluationCompletionTransaction completionTransaction;
	private final AiEvaluationClient aiEvaluationClient;
	private final SentenceVersionRepository sentenceVersionRepository;
	private final SentenceEvaluationRepository evaluationRepository;
	private final SentenceMoodRepository moodRepository;
	private final SentenceUsedDailyWordRepository usedDailyWordRepository;
	private final UserDailyEvaluationUsageRepository usageRepository;
	private final EvaluationPolicy evaluationPolicy;
	private final ServiceClock serviceClock;
	// 빈 존재 = evaluation.dispatch=rabbit. 프로퍼티 문자열 비교 대신 구성 자체를 스위치로 쓴다.
	private final ObjectProvider<EvaluationDispatchService> dispatchServiceProvider;

	public SentenceEvaluationResponse evaluate(AuthenticatedUser principal, Long versionId) {
		AuthenticatedUsers.requireUserId(principal);
		LocalDate serviceDate = serviceClock.today();
		dailyWordService.getToday(principal);
		EvaluationAttempt attempt = start(principal.userId(), versionId, serviceDate);

		EvaluationDispatchService dispatchService = dispatchServiceProvider.getIfAvailable();
		if (dispatchService != null) {
			// 접수는 위 start에서 확정됐다(사용량 차감·EVALUATING 전이). 발행 실패는 릴레이가
			// 보상하므로 여기서는 대기 응답을 바로 돌려주고, 결과는 프론트 폴링이 수신한다.
			dispatchService.dispatchAfterAccept(attempt.versionId());
			return SentenceEvaluationResponse.pending(attempt.versionId(), EvaluationStatus.EVALUATING);
		}

		try {
			// 결과 검증은 AiEvaluationClient 구현체(어댑터 경계)의 계약이다.
			AiEvaluationResult result = aiEvaluationClient.evaluate(attempt.command());
			if (result.safety().status() == SafetyStatus.REJECTED) {
				completionTransaction.completeRejected(attempt);
				throw new DomainException(ErrorCode.POLICY_REJECTED);
			}
			var evaluation = completionTransaction.completeSuccess(attempt, result);
			return SentenceEvaluationResponse.from(evaluation,
					moodRepository.findBySentenceVersionIdOrderByRankAsc(versionId),
					usedDailyWordRepository.findDetailBySentenceVersionId(versionId));
		} catch (DomainException exception) {
			if (exception.getErrorCode() == ErrorCode.POLICY_REJECTED) {
				throw exception;
			}
			completionTransaction.completeFailure(attempt);
			throw exception;
		} catch (RuntimeException exception) {
			// AI 연동 예외와 예기치 못한 오류 모두 실패 처리 후 500으로 변환한다.
			completionTransaction.completeFailure(attempt);
			throw new DomainException(ErrorCode.INTERNAL_ERROR, "AI evaluation could not be completed.", exception);
		}
	}

	@Transactional(readOnly = true)
	public SentenceEvaluationResponse get(AuthenticatedUser principal, Long versionId) {
		AuthenticatedUsers.requireUserId(principal);
		var version = sentenceVersionRepository.findWithSentenceById(versionId).orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND));
		if (!version.getSentence().belongsTo(principal.userId())) throw new DomainException(ErrorCode.NOT_FOUND);
		return evaluationRepository.findBySentenceVersionId(versionId)
				.map(evaluation -> SentenceEvaluationResponse.from(evaluation,
						moodRepository.findBySentenceVersionIdOrderByRankAsc(versionId),
						usedDailyWordRepository.findDetailBySentenceVersionId(versionId)))
				.orElseGet(() -> SentenceEvaluationResponse.pending(versionId, version.getEvaluationStatus()));
	}

	@Transactional(readOnly = true)
	public EvaluationUsageResponse todayUsage(AuthenticatedUser principal) {
		AuthenticatedUsers.requireUserId(principal);
		LocalDate today = serviceClock.today();
		int used = usageRepository.findByUserIdAndServiceDate(principal.userId(), today).map(value -> value.getUsedCount()).orElse(0);
		int limit = evaluationPolicy.getDailyEvaluationLimit();
		return new EvaluationUsageResponse(today, used, limit, Math.max(0, limit - used));
	}

	private EvaluationAttempt start(Long userId, Long versionId, LocalDate serviceDate) {
		try {
			return startTransaction.start(userId, versionId, serviceDate);
		} catch (DataIntegrityViolationException exception) {
			// 일일 사용량 행 생성 경합 시 1회 재시도한다. 두 번째 실패의 원인 추적을 위해 첫 실패를 기록한다.
			log.warn("Evaluation start raced for user {} version {}; retrying once", userId, versionId, exception);
			return startTransaction.start(userId, versionId, serviceDate);
		}
	}
}
