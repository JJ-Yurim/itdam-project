package com.ssafy.c206.backend.book.service;

import com.ssafy.c206.backend.book.domain.AiJobStatus;
import com.ssafy.c206.backend.book.repository.AiJobRepository;
import com.ssafy.c206.backend.boss.submission.repository.BossSubmissionRepository;
import com.ssafy.c206.backend.common.api.DomainException;
import com.ssafy.c206.backend.common.api.ErrorCode;
import com.ssafy.c206.backend.common.time.ServiceClock;
import com.ssafy.c206.backend.integration.book.BookGenerationMqContract;
import com.ssafy.c206.backend.integration.book.BookGenerationRequestMessage;
import com.ssafy.c206.backend.integration.book.BookGenerationRequester;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 미발행 AiJob을 잠그고 RabbitMQ에 at-least-once로 발행한다.
 *
 * <p>메시지에는 재료를 싣지 않는다(ADR-007). 생성 재료는 ai-service 소비자가 jobId로
 * DB에서 직접 읽으므로(ISSUE-122), 여기서는 실행 지시와 대조 식별자만 나른다.
 * 제출이 하나도 없는 보스런은 소비자까지 갈 필요 없이 발행 전에 걸러낸다.
 */
@Service
@RequiredArgsConstructor
public class BookGenerationDispatchService {
	private final AiJobRepository aiJobRepository;
	private final BossSubmissionRepository submissionRepository;
	private final BookGenerationRequester requester;
	private final ServiceClock serviceClock;

	@Transactional
	public boolean dispatch(Long jobId) {
		var job = aiJobRepository.findByIdForUpdate(jobId)
				.orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND));
		if (job.getStatus() != AiJobStatus.PENDING || job.isRequestDispatched()) {
			return false;
		}
		if (submissionRepository.countByBossRunId(job.getBossRun().getId()) == 0) {
			throw new DomainException(ErrorCode.VALIDATION_ERROR,
					"Book generation requires at least one boss submission.");
		}
		BookGenerationRequestMessage message = new BookGenerationRequestMessage(
				BookGenerationMqContract.REQUEST_CONTRACT_VERSION, job.getCorrelationId(), job.getId());

		// broker confirm을 받은 뒤에만 완료로 표시한다. 이후 DB commit 전에 죽으면 중복 발행될 수
		// 있으나 correlationId가 같고 결과 적용이 멱등이므로 메시지 유실보다 안전하다.
		requester.request(message);
		job.markRequestDispatched(serviceClock.now());
		return true;
	}
}
