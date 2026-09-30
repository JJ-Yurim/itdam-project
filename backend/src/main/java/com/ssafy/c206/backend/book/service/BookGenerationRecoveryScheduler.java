package com.ssafy.c206.backend.book.service;

import com.ssafy.c206.backend.book.domain.AiJobStatus;
import com.ssafy.c206.backend.book.repository.AiJobRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 커밋 직후 발행이 실패하거나 프로세스가 종료된 경우 미발행 Job을 다시 발행한다. */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "book.generation.client", havingValue = "rabbit")
public class BookGenerationRecoveryScheduler {
	private final AiJobRepository aiJobRepository;
	private final BookGenerationDispatchService dispatchService;

	@Scheduled(fixedDelayString = "${book.generation.dispatch-recovery-delay-ms:5000}")
	@SchedulerLock(name = "bookGenerationDispatchRecovery", lockAtLeastFor = "PT5S", lockAtMostFor = "PT2M")
	public void recoverPendingDispatches() {
		for (Long jobId : aiJobRepository.findPendingDispatchIds(AiJobStatus.PENDING, PageRequest.of(0, 50))) {
			try {
				dispatchService.dispatch(jobId);
			} catch (RuntimeException exception) {
				log.warn("book_generation_dispatch_recovery_failed job_id={} reason={}", jobId,
						exception.getMessage());
			}
		}
	}
}
