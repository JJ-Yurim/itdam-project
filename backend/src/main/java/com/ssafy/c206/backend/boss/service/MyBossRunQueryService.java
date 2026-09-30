package com.ssafy.c206.backend.boss.service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.ssafy.c206.backend.book.service.BookQueryService;
import com.ssafy.c206.backend.book.service.BookQueryService.BookSummary;
import com.ssafy.c206.backend.boss.api.MyBossRunResponse;
import com.ssafy.c206.backend.boss.domain.BossRun;
import com.ssafy.c206.backend.boss.domain.BossRunStatus;
import com.ssafy.c206.backend.boss.repository.BossRunRepository;
import com.ssafy.c206.backend.boss.submission.repository.BossSubmissionRepository;
import com.ssafy.c206.backend.common.api.DomainException;
import com.ssafy.c206.backend.common.api.ErrorCode;
import com.ssafy.c206.backend.common.api.PageResponse;
import com.ssafy.c206.backend.common.security.AuthenticatedUser;
import com.ssafy.c206.backend.common.security.AuthenticatedUsers;
import com.ssafy.c206.backend.common.time.ServiceClock;
import com.ssafy.c206.backend.user.service.UserQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MyBossRunQueryService {

	private final BossSubmissionRepository submissionRepository;
	private final BossRunRepository bossRunRepository;
	private final BookQueryService bookQueryService;
	private final BossRunLifecycleService lifecycleService;
	private final UserQueryService userQueryService;
	private final ServiceClock serviceClock;

	@Transactional(readOnly = true)
	public PageResponse<MyBossRunResponse> getMyBossRuns(AuthenticatedUser principal, BossRunStatus status,
			int page, int size) {
		long userId = AuthenticatedUsers.requireUserId(principal);
		userQueryService.getActiveUser(userId);
		Instant requestTime = serviceClock.now();
		if (bossRunRepository.existsPendingLifecycleTransition(requestTime)) {
			lifecycleService.syncAllRunStates(requestTime);
		}

		var pageable = PageRequest.of(page, size);
		var rows = status == null
				? submissionRepository.findParticipatedBossRuns(userId, pageable)
				: submissionRepository.findParticipatedBossRunsByStatus(userId, status, pageable);
		if (rows.isEmpty()) {
			return new PageResponse<>(List.of(), rows.getNumber(), rows.getSize(), rows.getTotalElements(),
					rows.getTotalPages(), rows.hasNext());
		}
		List<Long> runIds = rows.getContent().stream()
				.map(BossSubmissionRepository.ParticipatedBossRunRow::getBossRunId).toList();
		Map<Long, BossRun> runsById = bossRunRepository.findWithBossByIdIn(runIds).stream()
				.collect(Collectors.toMap(BossRun::getId, Function.identity()));
		Map<Long, BookSummary> booksByRunId = bookQueryService.findSummariesByBossRunIds(runIds);

		List<MyBossRunResponse> responses = rows.getContent().stream().map(row -> {
			BossRun run = requireBossRun(runsById, row.getBossRunId());
			Instant lastSubmittedAt = row.getLastSubmittedAt();
			BookSummary book = booksByRunId.get(run.getId());
			return MyBossRunResponse.from(run, lastSubmittedAt,
					book == null ? null : book.bookId(), book == null ? null : book.status());
		}).toList();
		return new PageResponse<>(responses, rows.getNumber(), rows.getSize(), rows.getTotalElements(),
				rows.getTotalPages(), rows.hasNext());
	}

	private BossRun requireBossRun(Map<Long, BossRun> runsById, Long bossRunId) {
		BossRun run = runsById.get(bossRunId);
		if (run == null) {
			throw new DomainException(ErrorCode.INTERNAL_ERROR, "Participated boss run details are missing.");
		}
		return run;
	}
}
