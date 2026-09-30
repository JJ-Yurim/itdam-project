package com.ssafy.c206.backend.book.service;

import java.time.Duration;

import com.ssafy.c206.backend.book.api.BookGenerationStatusResponse;
import com.ssafy.c206.backend.book.api.AiJobStatusResponse;
import com.ssafy.c206.backend.book.domain.AiJob;
import com.ssafy.c206.backend.book.domain.AiJobStatus;
import com.ssafy.c206.backend.book.domain.AiJobType;
import com.ssafy.c206.backend.book.repository.AiJobRepository;
import com.ssafy.c206.backend.book.repository.BookRepository;
import com.ssafy.c206.backend.common.api.DomainException;
import com.ssafy.c206.backend.common.api.ErrorCode;
import com.ssafy.c206.backend.common.security.AuthenticatedUser;
import com.ssafy.c206.backend.common.security.AuthenticatedUsers;
import com.ssafy.c206.backend.common.time.ServiceClock;
import com.ssafy.c206.backend.user.service.UserQueryService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * 상태 폴링 조회. 메서드 레벨 트랜잭션을 걸지 않는다 — 지연 실패 전이(REQUIRES_NEW)가
 * 커밋된 뒤 같은 영속성 컨텍스트로 재조회하면 1차 캐시의 stale PENDING이 돌아오기
 * 때문이다. 각 리포지토리 호출이 독립 트랜잭션을 타면 전이 결과가 그대로 보인다.
 */
@Service
public class BookGenerationStatusQueryService {
	private final BookRepository bookRepository;
	private final AiJobRepository aiJobRepository;
	private final UserQueryService userQueryService;
	private final BookGenerationFailureService failureService;
	private final ServiceClock serviceClock;
	private final Duration pendingTimeout;

	public BookGenerationStatusQueryService(BookRepository bookRepository, AiJobRepository aiJobRepository,
			UserQueryService userQueryService, BookGenerationFailureService failureService, ServiceClock serviceClock,
			@Value("${book.generation.pending-timeout-seconds:1800}") long pendingTimeoutSeconds) {
		this.bookRepository = bookRepository;
		this.aiJobRepository = aiJobRepository;
		this.userQueryService = userQueryService;
		this.failureService = failureService;
		this.serviceClock = serviceClock;
		this.pendingTimeout = Duration.ofSeconds(pendingTimeoutSeconds);
	}

	public BookGenerationStatusResponse getStatus(AuthenticatedUser principal, Long bossRunId) {
		AuthenticatedUsers.requireUserId(principal);
		userQueryService.getActiveUser(principal.userId());
		var job = aiJobRepository.findByBossRunIdAndJobType(bossRunId, AiJobType.BOOK_GENERATION)
				.orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND));
		if (failTimedOutJob(job)) {
			job = aiJobRepository.findById(job.getId()).orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND));
		}
		var book = bookRepository.findByBossRunId(bossRunId).orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND));
		return BookGenerationStatusResponse.from(book, job);
	}

	public AiJobStatusResponse getJobStatus(AuthenticatedUser principal, Long jobId) {
		AuthenticatedUsers.requireUserId(principal);
		userQueryService.getActiveUser(principal.userId());
		var job = aiJobRepository.findById(jobId)
				.orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND));
		if (failTimedOutJob(job)) {
			job = aiJobRepository.findById(jobId).orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND));
		}
		return AiJobStatusResponse.from(job);
	}

	/**
	 * 결과가 오지 않는 고아 PENDING의 지연 실패 판정(ISSUE-95). 폴링이 트리거하므로
	 * 스케줄러가 필요 없다. 전이가 일어나면 REQUIRES_NEW에서 커밋된 상태를 다시 읽도록
	 * true를 돌려준다. 이 사전 검사는 값싼 가드일 뿐이고 판정의 기준은
	 * {@link BookGenerationFailureService#failIfTimedOut}이 자기 트랜잭션에서 다시 세운다.
	 */
	private boolean failTimedOutJob(AiJob job) {
		if (job.getStatus() != AiJobStatus.PENDING
				|| serviceClock.now().isBefore(job.getUpdatedAt().plus(pendingTimeout))) {
			return false;
		}
		return failureService.failIfTimedOut(job.getId(), pendingTimeout);
	}
}
