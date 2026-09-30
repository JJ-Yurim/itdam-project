package com.ssafy.c206.backend.sentence.evaluation.service;

import java.time.Duration;
import java.time.Instant;

import com.ssafy.c206.backend.common.time.ServiceClock;
import com.ssafy.c206.backend.sentence.domain.EvaluationStatus;
import com.ssafy.c206.backend.sentence.repository.SentenceVersionRepository;
import com.ssafy.c206.backend.sentence.evaluation.repository.UserDailyEvaluationUsageRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Recovers an evaluation left EVALUATING by a process restart or cancellation. */
@Service
public class EvaluationRecoveryTransaction {

	private final SentenceVersionRepository versionRepository;
	private final UserDailyEvaluationUsageRepository usageRepository;
	private final ServiceClock serviceClock;
	private final Duration staleEvaluationTimeout;

	public EvaluationRecoveryTransaction(SentenceVersionRepository versionRepository,
			UserDailyEvaluationUsageRepository usageRepository, ServiceClock serviceClock,
			@Value("${evaluation.stale-after-seconds:900}") long staleAfterSeconds) {
		this.versionRepository = versionRepository;
		this.usageRepository = usageRepository;
		this.serviceClock = serviceClock;
		this.staleEvaluationTimeout = Duration.ofSeconds(staleAfterSeconds);
	}

	@Transactional
	public void recover(Long versionId) {
		var version = versionRepository.findWithSentenceByIdForUpdate(versionId).orElse(null);
		if (version == null || version.getEvaluationStatus() != EvaluationStatus.EVALUATING
				|| !version.isEvaluationStale(serviceClock.now(), staleEvaluationTimeout)) return;
		Instant now = serviceClock.now();
		version.markEvaluationFailed();
		if (version.getEvaluationStartedAt() != null) {
			usageRepository.findByUserIdAndServiceDateForUpdate(version.getSentence().getUser().getId(),
					serviceClock.dateAt(version.getEvaluationStartedAt())).ifPresent(usage -> usage.release(now));
		}
	}
}
