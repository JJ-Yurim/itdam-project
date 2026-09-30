package com.ssafy.c206.backend.book.api;

import com.ssafy.c206.backend.common.api.PageResponse;
import com.ssafy.c206.backend.book.service.LibraryQueryService;
import com.ssafy.c206.backend.common.security.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@RequestMapping("/api/v1/library/books")
@RequiredArgsConstructor
public class LibraryController implements LibraryApi {
	private final LibraryQueryService libraryQueryService;

	@GetMapping
	public PageResponse<LibraryBookSummaryResponse> list(
			@AuthenticationPrincipal AuthenticatedUser principal,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int size) {
		return libraryQueryService.list(principal, page, size);
	}

	@GetMapping("/{bookId}")
	public LibraryBookDetailResponse detail(
			@AuthenticationPrincipal AuthenticatedUser principal,
			@PathVariable Long bookId) {
		return libraryQueryService.detail(principal, bookId);
	}
}
