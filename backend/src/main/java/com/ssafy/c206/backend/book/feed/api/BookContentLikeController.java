package com.ssafy.c206.backend.book.feed.api;

import com.ssafy.c206.backend.book.feed.service.BookContentLikeService;
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
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class BookContentLikeController implements BookContentLikeApi {

	private final BookContentLikeService likeService;

	@Override
	@PutMapping("/book-contents/{bookContentId}/like")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void like(@AuthenticationPrincipal AuthenticatedUser principal, @PathVariable Long bookContentId) {
		likeService.like(principal, bookContentId);
	}

	@Override
	@DeleteMapping("/book-contents/{bookContentId}/like")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void unlike(@AuthenticationPrincipal AuthenticatedUser principal, @PathVariable Long bookContentId) {
		likeService.unlike(principal, bookContentId);
	}
}
