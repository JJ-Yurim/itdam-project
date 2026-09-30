package com.ssafy.c206.backend.book.service;

import java.time.Duration;

import com.ssafy.c206.backend.book.domain.AiJobStatus;
import com.ssafy.c206.backend.book.repository.AiJobRepository;
import com.ssafy.c206.backend.book.repository.BookRepository;
import com.ssafy.c206.backend.common.api.DomainException;
import com.ssafy.c206.backend.common.api.ErrorCode;
import com.ssafy.c206.backend.common.time.ServiceClock;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class BookGenerationFailureService {
	private final AiJobRepository aiJobRepository;
	private final BookRepository bookRepository;
	private final ServiceClock serviceClock;

	@Transactional
	public void markFailed(Long jobId, String message) {
		var job = aiJobRepository.findByIdForUpdate(jobId)
				.orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND));
		var now = serviceClock.now();
		job.markFailed(now, message == null ? "Book requester failed." : message);
		bookRepository.findByBossRunIdForUpdate(job.getBossRun().getId()).ifPresent(book -> book.fail(now));
	}

	/**
	 * 결과가 오지 않는 고아 PENDING job의 지연 실패 판정(ISSUE-95).
	 *
	 * <p>메시지가 재시도 끝에 DLQ로 빠지거나 커밋과 발행 사이에 프로세스가 죽으면
	 * job이 PENDING에 남는다. 폴링 조회가 이 판정을 트리거하므로 스케줄러가 필요
	 * 없다 — 보스 지연 만료와 같은 패턴이다. 호출부가 readOnly 트랜잭션이라
	 * REQUIRES_NEW로 분리하고, 시각 판정은 이 새 트랜잭션 안에서 다시 수행한다.
	 *
	 * <p>드물게 FAILED 전이 직후 뒤늦은 성공 결과가 도착하면 상태 전이의 터미널
	 * 가드가 CONFLICT로 거부하고 리스너가 버린다. 늦은 결과보다 "멈춘 화면"을
	 * 끝내주는 쪽을 우선한다.
	 */
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public boolean failIfTimedOut(Long jobId, Duration pendingTimeout) {
		var job = aiJobRepository.findByIdForUpdate(jobId).orElse(null);
		if (job == null || job.getStatus() != AiJobStatus.PENDING) {
			return false;
		}
		var now = serviceClock.now();
		if (now.isBefore(job.getUpdatedAt().plus(pendingTimeout))) {
			return false;
		}
		job.markFailed(now, "Book generation timed out while waiting for a result.");
		bookRepository.findByBossRunIdForUpdate(job.getBossRun().getId()).ifPresent(book -> book.fail(now));
		return true;
	}
}
