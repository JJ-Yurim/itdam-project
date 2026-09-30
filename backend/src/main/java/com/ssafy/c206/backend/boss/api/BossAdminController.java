package com.ssafy.c206.backend.boss.api;

import com.ssafy.c206.backend.boss.service.BossAdminService;
import com.ssafy.c206.backend.common.api.PageResponse;
import com.ssafy.c206.backend.common.security.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import com.ssafy.c206.backend.boss.domain.BossStatus;

/** 관리자 보스 원형 운영 API. ADMIN 세션 전용이며 API 계약은 BossAdminApi에 선언한다. 입력값 검증은 서비스 레이어에서 수행한다. */
@RestController
@RequestMapping("/api/admin/bosses")
@RequiredArgsConstructor
public class BossAdminController implements BossAdminApi {

	private final BossAdminService bossAdminService;

	@Override
	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public AdminBossResponse create(@AuthenticationPrincipal AuthenticatedUser principal,
			@RequestBody AdminBossCreateRequest request) {
		return bossAdminService.create(principal, request);
	}

	@Override
	@PutMapping("/{bossId}/book-cover")
	public AdminBossResponse changeBookCover(@AuthenticationPrincipal AuthenticatedUser principal,
			@PathVariable Long bossId, @RequestBody AdminBossBookCoverUpdateRequest request) {
		return bossAdminService.changeBookCover(principal, bossId, request);
	}

	@Override
	@PatchMapping("/{bossId}")
	public AdminBossResponse update(@AuthenticationPrincipal AuthenticatedUser principal,
			@PathVariable Long bossId, @RequestBody AdminBossUpdateRequest request) {
		return bossAdminService.update(principal, bossId, request);
	}

	@Override
	@PutMapping("/{bossId}/phase-templates")
	public AdminBossResponse updatePhaseTemplates(@AuthenticationPrincipal AuthenticatedUser principal,
			@PathVariable Long bossId, @RequestBody AdminBossPhaseTemplatesUpdateRequest request) {
		return bossAdminService.updatePhaseTemplates(principal, bossId, request);
	}

	@Override
	@PatchMapping("/{bossId}/status")
	public AdminBossResponse updateStatus(@AuthenticationPrincipal AuthenticatedUser principal,
			@PathVariable Long bossId, @RequestBody AdminBossStatusUpdateRequest request) {
		return bossAdminService.updateStatus(principal, bossId, request);
	}

	@Override
	@GetMapping
	public PageResponse<AdminBossResponse> getBosses(
			@RequestParam(required = false) String query,
			@RequestParam(required = false) BossStatus status,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int size) {
		return bossAdminService.getBosses(query, status, page, size);
	}

	@Override
	@GetMapping("/{bossId}/phase-templates")
	public AdminBossPhaseTemplatesResponse getPhaseTemplates(@PathVariable Long bossId) {
		return bossAdminService.getPhaseTemplates(bossId);
	}
}
