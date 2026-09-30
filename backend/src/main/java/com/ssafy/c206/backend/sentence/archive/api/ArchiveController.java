package com.ssafy.c206.backend.sentence.archive.api;

import com.ssafy.c206.backend.common.api.PageResponse;
import com.ssafy.c206.backend.sentence.archive.service.ArchiveQueryService;
import com.ssafy.c206.backend.common.security.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@RequestMapping("/api/v1/me/sentences")
@RequiredArgsConstructor
public class ArchiveController implements ArchiveApi {

	private final ArchiveQueryService archiveQueryService;

	@GetMapping
	public PageResponse<ArchiveSentenceResponse> getMyArchive(
			@AuthenticationPrincipal AuthenticatedUser principal,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int size) {
		return archiveQueryService.getMyArchive(principal, page, size);
	}

	@GetMapping("/unevaluated")
	public PageResponse<UnevaluatedArchiveSentenceResponse> getMyUnevaluatedArchive(
			@AuthenticationPrincipal AuthenticatedUser principal,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int size) {
		return archiveQueryService.getMyUnevaluatedArchive(principal, page, size);
	}

	@GetMapping("/{sentenceVersionId}")
	public ArchiveSentenceResponse getMyArchiveDetail(
			@AuthenticationPrincipal AuthenticatedUser principal,
			@PathVariable Long sentenceVersionId) {
		return archiveQueryService.getMyArchiveDetail(principal, sentenceVersionId);
	}
}
