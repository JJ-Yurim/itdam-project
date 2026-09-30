package com.ssafy.c206.backend.sentence.api;

import com.ssafy.c206.backend.sentence.service.SentenceCommandService;
import com.ssafy.c206.backend.sentence.service.SentenceQueryService;
import com.ssafy.c206.backend.common.security.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class SentenceController implements SentenceApi {
	private final SentenceCommandService commandService;
	private final SentenceQueryService queryService;

	@PostMapping("/sentences") @ResponseStatus(HttpStatus.CREATED)
	public SentenceVersionResponse create(@AuthenticationPrincipal AuthenticatedUser principal, @RequestBody SentenceRequest request) {
		return commandService.create(principal.userId(), request.content());
	}

	@PostMapping("/sentences/{sentenceId}/versions")
	@ResponseStatus(HttpStatus.CREATED)
	public SentenceVersionResponse createVersion(@AuthenticationPrincipal AuthenticatedUser principal, @PathVariable Long sentenceId, @RequestBody SentenceRequest request) {
		return commandService.createVersion(principal.userId(), sentenceId, request.content());
	}

	@PatchMapping("/sentence-versions/{versionId}")
	public SentenceVersionResponse updateDraft(@AuthenticationPrincipal AuthenticatedUser principal, @PathVariable Long versionId, @RequestBody SentenceRequest request) {
		return commandService.updateDraft(principal.userId(), versionId, request.content());
	}

	@GetMapping("/sentences/{sentenceId}")
	public SentenceResponse get(@AuthenticationPrincipal AuthenticatedUser principal, @PathVariable Long sentenceId) {
		return queryService.get(principal.userId(), sentenceId);
	}

	@GetMapping("/sentence-versions/{versionId}")
	public SentenceVersionResponse getVersion(@AuthenticationPrincipal AuthenticatedUser principal, @PathVariable Long versionId) {
		return queryService.getVersion(principal.userId(), versionId);
	}

	@DeleteMapping("/sentences/{sentenceId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void delete(@AuthenticationPrincipal AuthenticatedUser principal, @PathVariable Long sentenceId) {
		commandService.delete(principal.userId(), sentenceId);
	}
}
