package com.ssafy.c206.backend.book.api;

import com.ssafy.c206.backend.book.service.MyLibraryQueryService;
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
@RequestMapping("/api/v1/me/library/books")
@RequiredArgsConstructor
public class MyLibraryController implements MyLibraryApi {

	private final MyLibraryQueryService queryService;

	@Override
	@GetMapping
	public PageResponse<MyContributedBookResponse> getMyContributedBooks(
			@AuthenticationPrincipal AuthenticatedUser principal,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int size) {
		return queryService.getMyContributedBooks(principal, page, size);
	}
}
