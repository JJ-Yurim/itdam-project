package com.ssafy.c206.backend.sentence.evaluation.service;

import java.util.stream.Collectors;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import com.ssafy.c206.backend.common.api.DomainException;
import com.ssafy.c206.backend.common.api.ErrorCode;
import com.ssafy.c206.backend.common.time.ServiceClock;
import com.ssafy.c206.backend.dailyword.service.DailyWordReader;
import com.ssafy.c206.backend.integration.ai.AiEvaluationCommand;
import com.ssafy.c206.backend.integration.ai.DailyWordCard;
import com.ssafy.c206.backend.sentence.domain.EvaluationStatus;
import com.ssafy.c206.backend.sentence.domain.SentenceVersion;
import com.ssafy.c206.backend.sentence.evaluation.domain.UserDailyEvaluationUsage;
import com.ssafy.c206.backend.sentence.evaluation.repository.UserDailyEvaluationUsageRepository;
import com.ssafy.c206.backend.sentence.repository.SentenceVersionRepository;
import com.ssafy.c206.backend.user.domain.User;
import com.ssafy.c206.backend.user.service.UserQueryService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Value;

@Service
class EvaluationStartTransaction {

	private final SentenceVersionRepository sentenceVersionRepository;
	private final DailyWordReader dailyWordReader;
	private final UserDailyEvaluationUsageRepository usageRepository;
	private final UserQueryService userQueryService;
	private final EvaluationPolicy evaluationPolicy;
	private final ServiceClock serviceClock;
	private final Duration staleEvaluationTimeout;

	EvaluationStartTransaction(SentenceVersionRepository sentenceVersionRepository,
			DailyWordReader dailyWordReader,
			UserDailyEvaluationUsageRepository usageRepository, UserQueryService userQueryService,
			EvaluationPolicy evaluationPolicy, ServiceClock serviceClock,
			@Value("${evaluation.stale-after-seconds:900}") long staleAfterSeconds) {
		this.sentenceVersionRepository = sentenceVersionRepository;
		this.dailyWordReader = dailyWordReader;
		this.usageRepository = usageRepository;
		this.userQueryService = userQueryService;
		this.evaluationPolicy = evaluationPolicy;
		this.serviceClock = serviceClock;
		this.staleEvaluationTimeout = Duration.ofSeconds(staleAfterSeconds);
	}

	@Transactional
	EvaluationAttempt start(Long userId, Long versionId, LocalDate serviceDate) {
		User user = userQueryService.getActiveUser(userId);
		SentenceVersion version = sentenceVersionRepository.findWithSentenceByIdForUpdate(versionId)
				.orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND));
		if (!version.getSentence().belongsTo(userId)) {
			throw new DomainException(ErrorCode.NOT_FOUND);
		}
		Instant now = serviceClock.now();
		if (version.isEvaluationStale(now, staleEvaluationTimeout)) {
			version.markEvaluationFailed();
			if (version.getEvaluationStartedAt() != null) {
				usageRepository.findByUserIdAndServiceDateForUpdate(userId,
						serviceClock.dateAt(version.getEvaluationStartedAt())).ifPresent(usage -> usage.release(now));
			}
		}
		if (version.getEvaluationStatus() != EvaluationStatus.DRAFT
				&& version.getEvaluationStatus() != EvaluationStatus.EVALUATION_FAILED) {
			throw new DomainException(ErrorCode.CONFLICT, "The sentence version cannot be evaluated in its current state.");
		}

		var set = dailyWordReader.findSet(userId, serviceDate)
				.orElseThrow(() -> new DomainException(ErrorCode.CONFLICT, "Today's daily word set is unavailable."));
		var items = dailyWordReader.findItems(set.getId());
		if (items.size() != 5) {
			throw new DomainException(ErrorCode.CONFLICT, "Today's daily word set must contain five cards.");
		}

		UserDailyEvaluationUsage usage = usageRepository.findByUserIdAndServiceDateForUpdate(userId, serviceDate)
				.orElseGet(() -> usageRepository.saveAndFlush(UserDailyEvaluationUsage.start(user, serviceDate, now)));
		try {
			usage.consume(evaluationPolicy.getDailyEvaluationLimit(), now);
		} catch (UserDailyEvaluationUsage.EvaluationQuotaExceededException exception) {
			throw new DomainException(ErrorCode.RATE_LIMITED);
		}

		version.startEvaluation(now);
		Map<Long, Long> itemIdsByWordId = items.stream().collect(Collectors.toMap(
				item -> item.getWord().getId(), item -> item.getId()));
		List<DailyWordCard> cards = items.stream().map(item -> new DailyWordCard(item.getWord().getId(),
				item.getWord().getWord(), item.getWord().getPartOfSpeech(), item.getWord().getDefinition())).toList();
		return new EvaluationAttempt(userId, versionId, serviceDate,
				new AiEvaluationCommand(versionId, version.getContent(), serviceDate, cards), itemIdsByWordId);
	}
}
