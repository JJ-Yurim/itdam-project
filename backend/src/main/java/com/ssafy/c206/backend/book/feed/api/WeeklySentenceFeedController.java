package com.ssafy.c206.backend.book.feed.api;

import com.ssafy.c206.backend.book.feed.service.WeeklySentenceFeedService;
import com.ssafy.c206.backend.common.security.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class WeeklySentenceFeedController implements WeeklySentenceFeedApi {

	private final WeeklySentenceFeedService feedService;

	@Override
	@GetMapping("/sentences/feed")
	public WeeklySentenceFeedResponse getFeed(@AuthenticationPrincipal AuthenticatedUser principal) {
		return feedService.getFeed(principal);
	}
}
