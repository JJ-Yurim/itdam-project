package com.ssafy.c206.backend.boss.api;

import java.util.List;

import com.ssafy.c206.backend.common.api.PageResponse;
import com.ssafy.c206.backend.boss.domain.BossRunStatus;
import com.ssafy.c206.backend.boss.service.BossEligibleSentenceQueryService;
import com.ssafy.c206.backend.boss.service.BossQueryService;
import com.ssafy.c206.backend.boss.submission.api.BossSubmissionRequest;
import com.ssafy.c206.backend.boss.submission.api.BossSubmissionResponse;
import com.ssafy.c206.backend.boss.submission.api.BossSubmissionHistoryItem;
import com.ssafy.c206.backend.boss.submission.service.BossSubmissionService;
import com.ssafy.c206.backend.common.security.AuthenticatedUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@RestController
@Validated
@RequestMapping("/api/v1/boss-runs")
@RequiredArgsConstructor
public class BossController implements BossApi {

	private final BossQueryService bossQueryService;
	private final BossEligibleSentenceQueryService eligibleSentenceQueryService;
	private final BossSubmissionService bossSubmissionService;

	@GetMapping
	public PageResponse<BossRunSummaryResponse> getBossRuns(
			@AuthenticationPrincipal AuthenticatedUser principal,
			@RequestParam(required = false) List<BossRunStatus> status,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int size) {
		return bossQueryService.getRuns(principal, status, page, size);
	}

	@GetMapping("/{bossRunId}")
	public BossRunResponse getBossRun(@AuthenticationPrincipal AuthenticatedUser principal,
			@PathVariable Long bossRunId) {
		return bossQueryService.getRun(principal, bossRunId);
	}

	@GetMapping("/{bossRunId}/eligible-sentences")
	public PageResponse<EligibleSentenceResponse> getEligibleSentences(
			@AuthenticationPrincipal AuthenticatedUser principal, @PathVariable Long bossRunId,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int size) {
		return eligibleSentenceQueryService.getEligible(principal, bossRunId, page, size);
	}

	@PostMapping("/{bossRunId}/submissions")
	@ResponseStatus(HttpStatus.CREATED)
	public BossSubmissionResponse submit(@AuthenticationPrincipal AuthenticatedUser principal,
			@PathVariable Long bossRunId, @RequestBody BossSubmissionRequest request) {
		return BossSubmissionResponse.from(bossSubmissionService.submit(principal, bossRunId, request.sentenceVersionId(),
				request.submissionConsentAccepted()));
	}

	@GetMapping("/{bossRunId}/submissions/me")
	public PageResponse<BossSubmissionHistoryItem> getMySubmissions(@AuthenticationPrincipal AuthenticatedUser principal,
			@PathVariable Long bossRunId, @RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int size) {
		return bossSubmissionService.getMySubmissions(principal, bossRunId, page, size);
	}
}
