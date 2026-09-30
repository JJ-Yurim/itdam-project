package com.ssafy.c206.backend.book.api;

import java.util.List;

import com.ssafy.c206.backend.book.domain.BookStatus;
import com.ssafy.c206.backend.book.domain.BookVisibility;
import com.ssafy.c206.backend.book.service.BookAdminService;
import com.ssafy.c206.backend.common.api.PageResponse;
import com.ssafy.c206.backend.common.security.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/books")
@RequiredArgsConstructor
public class BookAdminController implements BookAdminApi {
	private final BookAdminService bookAdminService;

	@Override
	@GetMapping
	public PageResponse<AdminBookResponse> getBooks(
			@RequestParam(required = false) String query,
			@RequestParam(name = "status", required = false) List<BookStatus> statuses,
			@RequestParam(required = false) BookVisibility visibility,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int size) {
		return bookAdminService.getBooks(query, statuses, visibility, page, size);
	}

	@Override
	@PatchMapping("/{bookId}/visibility")
	public AdminBookResponse updateVisibility(@AuthenticationPrincipal AuthenticatedUser principal,
			@PathVariable Long bookId, @RequestBody AdminBookVisibilityUpdateRequest request) {
		return bookAdminService.updateVisibility(principal, bookId, request);
	}

	@Override
	@PutMapping("/{bookId}/cover")
	public AdminBookCoverResponse changeCover(@AuthenticationPrincipal AuthenticatedUser principal,
			@PathVariable Long bookId, @RequestBody AdminBookCoverUpdateRequest request) {
		return bookAdminService.changeCover(principal, bookId, request);
	}
}
