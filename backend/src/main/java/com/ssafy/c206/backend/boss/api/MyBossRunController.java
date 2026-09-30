package com.ssafy.c206.backend.boss.api;

import com.ssafy.c206.backend.boss.domain.BossRunStatus;
import com.ssafy.c206.backend.boss.service.MyBossRunQueryService;
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
@RequestMapping("/api/v1/me/boss-runs")
@RequiredArgsConstructor
public class MyBossRunController implements MyBossRunApi {

	private final MyBossRunQueryService queryService;

	@Override
	@GetMapping
	public PageResponse<MyBossRunResponse> getMyBossRuns(
			@AuthenticationPrincipal AuthenticatedUser principal,
			@RequestParam(required = false) BossRunStatus status,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int size) {
		return queryService.getMyBossRuns(principal, status, page, size);
	}
}
