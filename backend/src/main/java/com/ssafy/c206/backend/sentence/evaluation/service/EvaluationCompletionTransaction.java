package com.ssafy.c206.backend.sentence.evaluation.service;

import java.math.BigDecimal;
import java.time.Instant;

import com.ssafy.c206.backend.common.api.DomainException;
import com.ssafy.c206.backend.common.api.ErrorCode;
import com.ssafy.c206.backend.common.time.ServiceClock;
import com.ssafy.c206.backend.dailyword.service.DailyWordReader;
import com.ssafy.c206.backend.integration.ai.AiEvaluationResult;
import com.ssafy.c206.backend.integration.ai.MoodResult;
import com.ssafy.c206.backend.integration.ai.SafetyStatus;
import com.ssafy.c206.backend.integration.ai.UsedDailyWordResult;
import com.ssafy.c206.backend.integration.similarity.SentenceEmbeddingStore;
import com.ssafy.c206.backend.sentence.domain.EvaluationStatus;
import com.ssafy.c206.backend.sentence.domain.SentenceVersion;
import com.ssafy.c206.backend.sentence.evaluation.domain.SentenceEvaluation;
import com.ssafy.c206.backend.sentence.evaluation.domain.SentenceMood;
import com.ssafy.c206.backend.sentence.evaluation.domain.SentenceUsedDailyWord;
import com.ssafy.c206.backend.sentence.evaluation.repository.MoodCategoryRepository;
import com.ssafy.c206.backend.sentence.evaluation.repository.SentenceEvaluationRepository;
import com.ssafy.c206.backend.sentence.evaluation.repository.SentenceMoodRepository;
import com.ssafy.c206.backend.sentence.evaluation.repository.SentenceUsedDailyWordRepository;
import com.ssafy.c206.backend.sentence.evaluation.repository.UserDailyEvaluationUsageRepository;
import com.ssafy.c206.backend.sentence.repository.SentenceVersionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
class EvaluationCompletionTransaction {

	private final SentenceVersionRepository sentenceVersionRepository;
	private final SentenceEvaluationRepository evaluationRepository;
	private final SentenceMoodRepository sentenceMoodRepository;
	private final SentenceUsedDailyWordRepository usedDailyWordRepository;
	private final MoodCategoryRepository moodCategoryRepository;
	private final DailyWordReader dailyWordReader;
	private final UserDailyEvaluationUsageRepository usageRepository;
	private final EvaluationScoreCalculator scoreCalculator;
	private final SentenceEmbeddingStore embeddingStore;
	private final ServiceClock serviceClock;

	@Transactional
	SentenceEvaluation completeSuccess(EvaluationAttempt attempt, AiEvaluationResult result) {
		SentenceVersion version = evaluatingVersion(attempt.versionId());
		Instant now = serviceClock.now();
		EvaluationScores scores = new EvaluationScores(
				BigDecimal.valueOf(result.scores().completeness()), BigDecimal.valueOf(result.scores().originality()),
				BigDecimal.valueOf(result.scores().expression()), BigDecimal.valueOf(result.scores().imagery()),
				BigDecimal.valueOf(result.scores().emotionalImpact()));
		int usedCount = (int) result.usedDailyWords().stream().filter(UsedDailyWordResult::used).count();
		EvaluationScoreCalculation calculation = scoreCalculator.calculate(scores, usedCount);
		SentenceEvaluation evaluation = evaluationRepository.save(SentenceEvaluation.passed(version,
				result.scores().completeness(), result.scores().originality(), result.scores().expression(),
				result.scores().imagery(), result.scores().emotionalImpact(), calculation.baseScore(), calculation.wordBonus(),
				calculation.gameBaseAttack(), result.overallFeedback(), result.modelName(), result.promptVersion(),
				result.rubricVersion(), now));
		for (MoodResult mood : result.moods()) {
			var category = moodCategoryRepository.findByCode(mood.categoryCode())
					.orElseThrow(() -> new DomainException(ErrorCode.INTERNAL_ERROR, "Mood taxonomy is unavailable."));
			sentenceMoodRepository.save(SentenceMood.of(version, category, mood.strength(), mood.rank()));
		}
		for (UsedDailyWordResult used : result.usedDailyWords()) {
			if (used.used()) {
				Long itemId = attempt.dailyWordItemIds().get(used.wordId());
				var item = dailyWordReader.findItem(itemId)
						.orElseThrow(() -> new DomainException(ErrorCode.INTERNAL_ERROR, "Daily word item is unavailable."));
				usedDailyWordRepository.save(SentenceUsedDailyWord.of(version, item));
			}
		}
		// 임베딩은 평가 결과와 같은 트랜잭션에 저장한다. 평가만 남고 벡터가 빠지면
		// 해당 문장은 이후 유사도 비교에서 영구히 누락된다.
		embeddingStore.save(version.getId(), result, now);
		version.markEvaluated(now);
		return evaluation;
	}

	@Transactional
	void completeRejected(EvaluationAttempt attempt) {
		SentenceVersion version = evaluatingVersion(attempt.versionId());
		version.markRejected(serviceClock.now());
	}

	@Transactional
	void completeFailure(EvaluationAttempt attempt) {
		SentenceVersion version = sentenceVersionRepository.findWithSentenceByIdForUpdate(attempt.versionId())
				.orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND));
		if (version.getEvaluationStatus() == EvaluationStatus.EVALUATING) {
			Instant now = serviceClock.now();
			version.markEvaluationFailed();
			usageRepository.findByUserIdAndServiceDateForUpdate(attempt.userId(), attempt.serviceDate())
					.ifPresent(usage -> usage.release(now));
		}
	}

	private SentenceVersion evaluatingVersion(Long versionId) {
		SentenceVersion version = sentenceVersionRepository.findWithSentenceByIdForUpdate(versionId)
				.orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND));
		if (version.getEvaluationStatus() != EvaluationStatus.EVALUATING) {
			throw new DomainException(ErrorCode.CONFLICT, "The evaluation is no longer active.");
		}
		return version;
	}
}
