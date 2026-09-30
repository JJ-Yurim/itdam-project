package com.ssafy.c206.backend.boss.leaderboard.api;

import com.ssafy.c206.backend.boss.leaderboard.service.BossRunLeaderboardService;
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
public class BossRunLeaderboardController implements BossRunLeaderboardApi {

	private final BossRunLeaderboardService leaderboardService;

	@GetMapping("/{bossRunId}/leaderboard")
	public BossRunLeaderboardResponse getLeaderboard(@AuthenticationPrincipal AuthenticatedUser principal,
			@PathVariable Long bossRunId) {
		return leaderboardService.getLeaderboard(principal, bossRunId);
	}
}
