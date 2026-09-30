package com.ssafy.c206.backend.book.reaction.api;

import com.ssafy.c206.backend.book.api.LibraryBookSummaryResponse;
import com.ssafy.c206.backend.book.reaction.service.MyBookmarkQueryService;
import com.ssafy.c206.backend.common.api.PageResponse;
import com.ssafy.c206.backend.common.security.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@RequestMapping("/api/v1/me/bookmarks")
@RequiredArgsConstructor
public class MyBookmarkController implements MyBookmarkApi {

	private final MyBookmarkQueryService queryService;

	@Override
	@GetMapping
	public PageResponse<LibraryBookSummaryResponse> getMyBookmarks(
			@AuthenticationPrincipal AuthenticatedUser principal,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int size) {
		return queryService.getMyBookmarks(principal, page, size);
	}
}
