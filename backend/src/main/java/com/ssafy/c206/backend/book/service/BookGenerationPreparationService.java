package com.ssafy.c206.backend.book.service;

import java.time.Instant;

import com.ssafy.c206.backend.book.domain.AiJob;
import com.ssafy.c206.backend.book.domain.AiJobStatus;
import com.ssafy.c206.backend.book.domain.AiJobType;
import com.ssafy.c206.backend.book.domain.Book;
import com.ssafy.c206.backend.book.repository.AiJobRepository;
import com.ssafy.c206.backend.book.repository.BookRepository;
import com.ssafy.c206.backend.boss.domain.BossRun;
import com.ssafy.c206.backend.boss.domain.BossRunStatus;
import com.ssafy.c206.backend.boss.service.BossRunReader;
import com.ssafy.c206.backend.common.api.DomainException;
import com.ssafy.c206.backend.common.api.ErrorCode;
import com.ssafy.c206.backend.common.time.ServiceClock;
import com.ssafy.c206.backend.integration.book.BookGenerationRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class BookGenerationPreparationService {
	private final BossRunReader bossRunReader;
	private final BookRepository bookRepository;
	private final AiJobRepository aiJobRepository;
	private final BookGenerationDispatchService dispatchService;
	private final ServiceClock serviceClock;

	@Transactional
	public BookGenerationPreparation prepareAfterClear(Long bossRunId) {
		Instant now = serviceClock.now();
		BossRun run = bossRunReader.getRunWithBossForUpdate(bossRunId);
		if (run.getStatus() != BossRunStatus.CLEARED) {
			throw new DomainException(ErrorCode.CONFLICT, "Book generation requires a cleared boss run.");
		}
		Book book = bookRepository.findByBossRunId(bossRunId).orElseGet(() -> bookRepository.save(
				Book.generating(run, run.getBoss().getBookCoverImageAsset(), now)));
		AiJob job = aiJobRepository.findByBossRunIdAndJobType(bossRunId, AiJobType.BOOK_GENERATION)
				.orElseGet(() -> aiJobRepository.save(AiJob.pending(run, now)));
		boolean shouldRequest = job.getStatus() == AiJobStatus.PENDING && !job.isRequestDispatched();
		return new BookGenerationPreparation(new BookGenerationRequest(job.getId(), run.getId(), book.getId()), shouldRequest);
	}

	/**
	 * Called only after {@link #prepareAfterClear(Long)} has committed.
	 * 발행 실패는 미발행 상태를 유지하며 복구 스케줄러가 다시 시도한다.
	 */
	public void requestAfterCommit(BookGenerationPreparation preparation) {
		if (!preparation.requestRequired()) {
			return;
		}
		try {
			dispatchService.dispatch(preparation.request().jobId());
		} catch (RuntimeException exception) {
			log.warn("book_generation_dispatch_deferred job_id={} reason={}", preparation.request().jobId(),
					exception.getMessage());
		}
	}
}
