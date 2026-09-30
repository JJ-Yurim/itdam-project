package com.ssafy.c206.backend.boss.submission.service;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.ssafy.c206.backend.boss.api.BossSubmissionSummary;
import com.ssafy.c206.backend.boss.domain.BossRunStatus;
import com.ssafy.c206.backend.boss.service.BossRunLifecycleService;
import com.ssafy.c206.backend.book.service.BookGenerationPreparation;
import com.ssafy.c206.backend.book.service.BookGenerationPreparationService;
import com.ssafy.c206.backend.boss.submission.api.BossSubmissionHistoryItem;
import com.ssafy.c206.backend.common.api.PageResponse;
import com.ssafy.c206.backend.common.time.ServiceClock;
import com.ssafy.c206.backend.boss.submission.repository.BossSubmissionRepository;
import com.ssafy.c206.backend.common.security.AuthenticatedUser;
import com.ssafy.c206.backend.common.security.AuthenticatedUsers;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.Page;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class BossSubmissionService {
	private final BossRunLifecycleService lifecycleService;
	private final BossSubmissionTransaction transaction;
	private final BossSubmissionRepository submissionRepository;
	private final BookGenerationPreparationService bookGenerationPreparationService;
	private final ServiceClock serviceClock;

	public BossSubmissionResult submit(AuthenticatedUser principal, Long bossRunId, Long sentenceVersionId,
			boolean submissionConsentAccepted) {
		AuthenticatedUsers.requireUserId(principal);
		// One request timestamp is shared by the lifecycle sync, service-date attribution and
		// the write transaction so a boundary crossing cannot leave ACTIVE behind.
		Instant requestTime = serviceClock.now();
		lifecycleService.syncStateIfNeeded(bossRunId, requestTime);
		// 검증·유사도·데미지 계산은 잠금 없이 먼저 끝내고, 회차 행을 잠그는 구간은 HP 반영만 담당한다.
		// 두 단계가 서로 다른 트랜잭션이므로 반영 단계가 회차 상태와 페이즈를 다시 확인한다.
		BossSubmissionPreparation submissionPreparation = transaction.prepare(principal.userId(), bossRunId,
				sentenceVersionId, submissionConsentAccepted, requestTime);
		BossSubmissionResult result = transaction.apply(submissionPreparation, requestTime);
		if (result.bossRunStatus() == BossRunStatus.CLEARED) {
			BookGenerationPreparation preparation = bookGenerationPreparationService.prepareAfterClear(bossRunId);
			bookGenerationPreparationService.requestAfterCommit(preparation);
		}
		return result;
	}

	/** 다른 도메인(보관함 등)이 문장 버전별 제출 요약을 조회할 때 사용하는 경계 메서드. */
	@Transactional(readOnly = true)
	public Map<Long, List<BossSubmissionSummary>> getSummariesBySentenceVersionIds(Collection<Long> sentenceVersionIds) {
		if (sentenceVersionIds.isEmpty()) {
			return Map.of();
		}
		return submissionRepository.findDetailsBySentenceVersionIdIn(sentenceVersionIds).stream()
				.collect(Collectors.groupingBy(submission -> submission.getSentenceVersion().getId(),
						Collectors.mapping(BossSubmissionSummary::from, Collectors.toList())));
	}

	@Transactional(readOnly = true)
	public List<BossSubmissionSummary> getSummariesBySentenceVersionId(Long sentenceVersionId) {
		return submissionRepository.findDetailsBySentenceVersionId(sentenceVersionId).stream()
				.map(BossSubmissionSummary::from).toList();
	}

	@Transactional(readOnly = true)
	public PageResponse<BossSubmissionHistoryItem> getMySubmissions(AuthenticatedUser principal, Long bossRunId,
			int page, int size) {
		AuthenticatedUsers.requireUserId(principal);
		PageRequest pageable = PageRequest.of(page, size, Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")));
		return PageResponse.from(
				submissionRepository.findByBossRunIdAndUserIdOrderByCreatedAtDesc(bossRunId, principal.userId(), pageable)
						.map(BossSubmissionHistoryItem::from));
	}
}
