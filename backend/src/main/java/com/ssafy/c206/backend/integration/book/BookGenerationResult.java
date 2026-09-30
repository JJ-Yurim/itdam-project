package com.ssafy.c206.backend.integration.book;

import java.util.List;

/**
 * 결과 경계. 성공이면 기여 추적용 본문 블록과 독자가 읽을 전문(body), 목록 요약(summary)이
 * 함께 오고(ISSUE-96),
 * 실패면 사유만 온다. contentType은 여기서 검증하지 않는다 — 알 수 없는 값은
 * 저장 트랜잭션이 계약 위반으로 판정해 결과 전체를 거부한다.
 */
public record BookGenerationResult(
		Long jobId,
		String correlationId,
		boolean success,
		String errorMessage,
		List<Content> contents,
		String body,
		String summary,
		List<Long> usedSubmissionIds,
		List<Long> excludedSubmissionIds,
		String modelName,
		String promptVersion) {

	public record Content(int sequenceNo, String contentType, String content, Long sourceSubmissionId) { }

	public static BookGenerationResult success(Long jobId, String correlationId, List<Content> contents,
			String body, String summary, List<Long> usedSubmissionIds, List<Long> excludedSubmissionIds,
			String modelName, String promptVersion) {
		return new BookGenerationResult(jobId, correlationId, true, null,
				contents == null ? List.of() : List.copyOf(contents),
				body, summary,
				usedSubmissionIds == null ? List.of() : List.copyOf(usedSubmissionIds),
				excludedSubmissionIds == null ? List.of() : List.copyOf(excludedSubmissionIds),
				modelName, promptVersion);
	}

	public static BookGenerationResult failure(Long jobId, String correlationId, String errorMessage) {
		return new BookGenerationResult(jobId, correlationId, false, errorMessage, List.of(), null, null,
				List.of(), List.of(), null, null);
	}
}
