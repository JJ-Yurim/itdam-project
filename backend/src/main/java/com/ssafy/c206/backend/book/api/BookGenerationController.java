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
@RequestMapping("/api/v1/boss-runs")
@RequiredArgsConstructor
public class BookGenerationController implements BookGenerationApi {
	private final BookGenerationStatusQueryService statusService;

	@GetMapping("/{bossRunId}/book-generation")
	public BookGenerationStatusResponse getStatus(
			@AuthenticationPrincipal AuthenticatedUser principal,
			@PathVariable Long bossRunId) {
		return statusService.getStatus(principal, bossRunId);
	}
}
