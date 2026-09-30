package com.ssafy.c206.backend.dailyword.api;

import java.time.Instant;
import java.time.LocalDate;

import com.ssafy.c206.backend.common.security.AuthenticatedUser;
import com.ssafy.c206.backend.common.time.ServiceClock;
import com.ssafy.c206.backend.dailyword.service.DailyWordPreviewCachePolicy;
import com.ssafy.c206.backend.dailyword.service.DailyWordPreviewService;
import com.ssafy.c206.backend.dailyword.service.DailyWordService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/daily-words")
@RequiredArgsConstructor
public class DailyWordController implements DailyWordApi {

	private final DailyWordService dailyWordService;
	private final DailyWordPreviewService dailyWordPreviewService;
	private final DailyWordPreviewCachePolicy previewCachePolicy;
	private final ServiceClock serviceClock;

	@GetMapping("/today")
	public DailyWordResponse today(@AuthenticationPrincipal AuthenticatedUser principal) {
		return dailyWordService.getToday(principal);
	}

	@GetMapping("/preview")
	public ResponseEntity<DailyWordPreviewResponse> preview() {
		Instant requestTime = serviceClock.now();
		LocalDate serviceDate = serviceClock.dateAt(requestTime);
		DailyWordPreviewResponse response = dailyWordPreviewService.getPreview(serviceDate);
		return ResponseEntity.ok()
				.cacheControl(CacheControl.maxAge(previewCachePolicy.maxAge(response.serviceDate(), requestTime))
						.cachePublic())
				.body(response);
	}
}
