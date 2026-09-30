package com.ssafy.c206.backend.book.api;

import com.ssafy.c206.backend.book.service.BookGenerationStatusQueryService;
import com.ssafy.c206.backend.common.security.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/ai-jobs")
@RequiredArgsConstructor
public class AiJobController implements AiJobApi {
	private final BookGenerationStatusQueryService statusService;

	@GetMapping("/{jobId}")
	public AiJobStatusResponse get(
			@AuthenticationPrincipal AuthenticatedUser principal,
			@PathVariable Long jobId) {
		return statusService.getJobStatus(principal, jobId);
	}
}
