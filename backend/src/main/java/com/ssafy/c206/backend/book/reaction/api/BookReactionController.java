package com.ssafy.c206.backend.book.reaction.api;

import com.ssafy.c206.backend.book.reaction.service.BookReactionService;
import com.ssafy.c206.backend.common.security.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/books/{bookId}")
@RequiredArgsConstructor
public class BookReactionController implements BookReactionApi {

	private final BookReactionService reactionService;

	@Override
	@PutMapping("/like")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void like(@AuthenticationPrincipal AuthenticatedUser principal, @PathVariable Long bookId) {
		reactionService.like(principal, bookId);
	}

	@Override
	@DeleteMapping("/like")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void unlike(@AuthenticationPrincipal AuthenticatedUser principal, @PathVariable Long bookId) {
		reactionService.unlike(principal, bookId);
	}

	@Override
	@PutMapping("/bookmark")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void bookmark(@AuthenticationPrincipal AuthenticatedUser principal, @PathVariable Long bookId) {
		reactionService.bookmark(principal, bookId);
	}

	@Override
	@DeleteMapping("/bookmark")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void unbookmark(@AuthenticationPrincipal AuthenticatedUser principal, @PathVariable Long bookId) {
		reactionService.unbookmark(principal, bookId);
	}
}
