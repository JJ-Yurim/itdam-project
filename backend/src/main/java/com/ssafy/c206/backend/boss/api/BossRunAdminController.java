package com.ssafy.c206.backend.boss.api;

import java.time.Instant;
import java.util.List;

import com.ssafy.c206.backend.boss.domain.BossRunStatus;
import com.ssafy.c206.backend.boss.service.BossRunAdminService;
import com.ssafy.c206.backend.common.api.PageResponse;
import com.ssafy.c206.backend.common.security.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 관리자 보스런 회차 운영 API. ADMIN 세션 전용이며 API 계약은 BossRunAdminApi에 선언한다. 입력값 검증은 서비스 레이어에서 수행한다. */
@RestController
@RequestMapping("/api/admin/boss-runs")
@RequiredArgsConstructor
public class BossRunAdminController implements BossRunAdminApi {

	private final BossRunAdminService bossRunAdminService;

	@Override
	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public AdminBossRunResponse create(@AuthenticationPrincipal AuthenticatedUser principal,
			@RequestBody AdminBossRunCreateRequest request) {
		return bossRunAdminService.create(principal, request);
	}

	@Override
	@PatchMapping("/{runId}")
	public AdminBossRunResponse update(@AuthenticationPrincipal AuthenticatedUser principal,
			@PathVariable Long runId, @RequestBody AdminBossRunUpdateRequest request) {
		return bossRunAdminService.update(principal, runId, request);
	}

	@Override
	@PostMapping("/{runId}/activate")
	public AdminBossRunResponse activate(@AuthenticationPrincipal AuthenticatedUser principal,
			@PathVariable Long runId, @RequestBody AdminBossRunActionRequest request) {
		return bossRunAdminService.activate(principal, runId, request);
	}

	@Override
	@PostMapping("/{runId}/cancel")
	public AdminBossRunResponse cancel(@AuthenticationPrincipal AuthenticatedUser principal,
			@PathVariable Long runId, @RequestBody AdminBossRunActionRequest request) {
		return bossRunAdminService.cancel(principal, runId, request);
	}

	@Override
	@PutMapping("/{runId}/phases/{phaseNo}/image")
	public AdminBossRunResponse changePhaseImage(@AuthenticationPrincipal AuthenticatedUser principal,
			@PathVariable Long runId, @PathVariable int phaseNo,
			@RequestBody AdminBossPhaseImageUpdateRequest request) {
		return bossRunAdminService.changePhaseImage(principal, runId, phaseNo, request);
	}

	@Override
	@PostMapping("/{runId}/end")
	public AdminBossRunResponse end(@AuthenticationPrincipal AuthenticatedUser principal,
			@PathVariable Long runId, @RequestBody AdminBossRunActionRequest request) {
		return bossRunAdminService.end(principal, runId, request);
	}

	@Override
	@GetMapping
	public PageResponse<AdminBossRunResponse> getRuns(
			@RequestParam(required = false) String query,
			@RequestParam(name = "status", required = false) List<BossRunStatus> statuses,
			@RequestParam(required = false) Instant from,
			@RequestParam(required = false) Instant to,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int size) {
		return bossRunAdminService.getRuns(query, statuses, from, to, page, size);
	}
}
