package com.ssafy.c206.backend.integration.book;

import java.util.List;
import java.util.HashSet;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.ssafy.c206.backend.common.api.DomainException;
import com.ssafy.c206.backend.common.api.ErrorCode;

/**
 * book.generation.result 큐에서 받는 결과 페이로드.
 *
 * <p>성공이면 ai-service의 BookGenerationResponse와 생성 추적 메타데이터가 담기고,
 * 실패면 errorCode/errorMessage가 담긴다. 성공 결과는 전체 제출의 사용/제외
 * 내역과 본문 출처가 일치해야 영속화 경계로 전달된다.
 *
 * <p>ai-service가 필드를 추가해도 역직렬화가 깨지지 않도록 미지 필드를 무시한다.
 * 평가 응답에서 임베딩 필드 추가로 전체 평가가 중단됐던 회귀의 재발 방지다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record BookGenerationResultMessage(
		String contractVersion,
		Long jobId,
		String correlationId,
		boolean success,
		String errorCode,
		String errorMessage,
		List<ContentBlock> contents,
		String body,
		String summary,
		List<Long> usedSubmissionIds,
		List<Long> excludedSubmissionIds,
		String modelName,
		String promptVersion) {

	/** ai_jobs.last_error 컬럼 상한. 이보다 긴 실패 사유는 잘라서 저장한다. */
	private static final int MAX_ERROR_LENGTH = 500;

	@JsonIgnoreProperties(ignoreUnknown = true)
	public record ContentBlock(Integer sequenceNo, String contentType, String content, Long sourceSubmissionId) {
	}

	public BookGenerationResult toResult() {
		validateEnvelope();
		if (success) {
			validateSuccessContract();
			return BookGenerationResult.success(jobId, correlationId,
					contents.stream().map(block -> new BookGenerationResult.Content(
							block.sequenceNo() == null ? 0 : block.sequenceNo(),
							block.contentType(), block.content(), block.sourceSubmissionId())).toList(),
					body, summary, usedSubmissionIds, excludedSubmissionIds, modelName, promptVersion);
		}
		return BookGenerationResult.failure(jobId, correlationId, errorSummary());
	}

	private void validateEnvelope() {
		if (!BookGenerationMqContract.RESULT_CONTRACT_VERSION.equals(contractVersion)) {
			throw invalid("Unsupported book result contract version: " + contractVersion);
		}
		if (jobId == null || jobId <= 0) {
			throw invalid("Book result jobId must be positive.");
		}
		try {
			UUID.fromString(correlationId);
		} catch (IllegalArgumentException | NullPointerException exception) {
			throw invalid("Book result correlationId must be a UUID.");
		}
	}

	private void validateSuccessContract() {
		if (contents == null || contents.isEmpty() || usedSubmissionIds == null || excludedSubmissionIds == null
				|| modelName == null || modelName.isBlank() || promptVersion == null || promptVersion.isBlank()) {
			throw invalid("Successful book result fields are incomplete.");
		}
		List<Long> sources = contents.stream().map(ContentBlock::sourceSubmissionId)
				.filter(id -> id != null).toList();
		if (!sources.equals(usedSubmissionIds) || new HashSet<>(usedSubmissionIds).size() != usedSubmissionIds.size()
				|| new HashSet<>(excludedSubmissionIds).size() != excludedSubmissionIds.size()) {
			throw invalid("Book result submission provenance is inconsistent.");
		}
		if (usedSubmissionIds.stream().anyMatch(id -> id == null || id <= 0)
				|| excludedSubmissionIds.stream().anyMatch(id -> id == null || id <= 0)) {
			throw invalid("Book result submission ids must be positive.");
		}
		HashSet<Long> overlap = new HashSet<>(usedSubmissionIds);
		overlap.retainAll(excludedSubmissionIds);
		if (!overlap.isEmpty()) {
			throw invalid("A submission cannot be both used and excluded.");
		}
	}

	private static DomainException invalid(String message) {
		return new DomainException(ErrorCode.VALIDATION_ERROR, message);
	}

	private String errorSummary() {
		// 지역 변수 이름이 record 컴포넌트 summary(책 요약)를 가리지 않게 구분한다.
		String reason;
		if (errorCode == null || errorCode.isBlank()) {
			reason = errorMessage;
		} else if (errorMessage == null || errorMessage.isBlank()) {
			reason = errorCode;
		} else {
			reason = errorCode + ": " + errorMessage;
		}
		if (reason == null) {
			return "Book generation failed without a reported reason.";
		}
		return reason.length() > MAX_ERROR_LENGTH ? reason.substring(0, MAX_ERROR_LENGTH) : reason;
	}
}
