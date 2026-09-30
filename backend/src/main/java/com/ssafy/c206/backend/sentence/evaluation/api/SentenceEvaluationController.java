package com.ssafy.c206.backend.sentence.evaluation.api;

import com.ssafy.c206.backend.sentence.evaluation.service.SentenceEvaluationService;
import com.ssafy.c206.backend.common.security.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class SentenceEvaluationController implements SentenceEvaluationApi {

	private final SentenceEvaluationService sentenceEvaluationService;

	@PostMapping("/sentence-versions/{versionId}/evaluations")
	public SentenceEvaluationResponse evaluate(@AuthenticationPrincipal AuthenticatedUser principal,
			@PathVariable Long versionId) {
		return sentenceEvaluationService.evaluate(principal, versionId);
	}

	@GetMapping("/sentence-versions/{versionId}/evaluation")
	public SentenceEvaluationResponse get(@AuthenticationPrincipal AuthenticatedUser principal,
			@PathVariable Long versionId) {
		return sentenceEvaluationService.get(principal, versionId);
	}

	@GetMapping("/evaluation-usage/today")
	public EvaluationUsageResponse usage(@AuthenticationPrincipal AuthenticatedUser principal) {
		return sentenceEvaluationService.todayUsage(principal);
	}
}
