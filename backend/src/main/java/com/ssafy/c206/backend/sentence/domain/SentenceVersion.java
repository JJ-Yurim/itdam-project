package com.ssafy.c206.backend.sentence.domain;

import java.time.Instant;
import java.util.UUID;
import java.time.Duration;

import com.ssafy.c206.backend.common.api.DomainException;
import com.ssafy.c206.backend.common.api.ErrorCode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "sentence_versions", uniqueConstraints = @UniqueConstraint(
		name = "uk_sentence_version_no", columnNames = {"sentence_id", "version_no"}),
		indexes = @Index(name = "idx_sentence_version_status", columnList = "evaluation_status"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SentenceVersion {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "sentence_id", nullable = false)
	private Sentence sentence;

	@Column(name = "version_no", nullable = false)
	private int versionNo;

	@NotBlank
	@Size(max = 500)
	@Column(nullable = false, columnDefinition = "text")
	private String content;

	@Enumerated(EnumType.STRING)
	@Column(name = "evaluation_status", nullable = false, length = 30)
	private EvaluationStatus evaluationStatus;

	@Column(nullable = false, updatable = false)
	private Instant createdAt;

	@Column
	private Instant confirmedAt;

	@Column(name = "evaluation_started_at")
	private Instant evaluationStartedAt;

	/**
	 * 비동기 평가(ISSUE-208)의 발행 상태. EVALUATING이면서 아직 발행되지 않은 행을
	 * 릴레이가 찾아 발행한다. 동기 모드에서는 쓰이지 않는다.
	 */
	@Column(name = "evaluation_dispatched", nullable = false)
	private boolean evaluationDispatched;

	/**
	 * 이번 평가 시도의 상관 ID. 결과 메시지가 이 값과 일치할 때만 적용해, 회수 후 재요청된
	 * 버전에 이전 시도의 늦은 결과가 섞이지 않게 한다. 시도마다 새로 발급된다.
	 */
	@Column(name = "evaluation_correlation_id")
	private UUID evaluationCorrelationId;

	private SentenceVersion(Sentence sentence, int versionNo, String content, Instant now) {
		this.sentence = sentence;
		this.versionNo = versionNo;
		this.content = normalizeContent(content);
		this.evaluationStatus = EvaluationStatus.DRAFT;
		this.createdAt = now;
	}

	public static SentenceVersion draft(Sentence sentence, int versionNo, String content, Instant now) {
		return new SentenceVersion(sentence, versionNo, content, now);
	}

	public void updateDraft(String content) {
		ensureDraft();
		this.content = normalizeContent(content);
	}

	public void startEvaluation(Instant startedAt) {
		ensureMutable();
		if (evaluationStatus != EvaluationStatus.DRAFT && evaluationStatus != EvaluationStatus.EVALUATION_FAILED) {
			throw new DomainException(ErrorCode.CONFLICT, "Only a draft or failed version can be evaluated.");
		}
		evaluationStatus = EvaluationStatus.EVALUATING;
		evaluationStartedAt = startedAt;
		// 시도 단위 fencing 토큰. 동기 모드에서는 발행되지 않아 무해하고, 비동기 모드에서는
		// 이 값이 요청·결과를 한 시도로 묶는다.
		evaluationCorrelationId = UUID.randomUUID();
		evaluationDispatched = false;
	}

	/** 발행 릴레이가 confirm을 받은 뒤 호출한다. 같은 시도의 재발행을 막는다. */
	public void markEvaluationDispatched() {
		if (evaluationStatus != EvaluationStatus.EVALUATING) {
			throw new DomainException(ErrorCode.CONFLICT, "Only an evaluating version can be marked dispatched.");
		}
		evaluationDispatched = true;
	}

	public void markEvaluated(Instant confirmedAt) {
		ensureMutable();
		if (evaluationStatus != EvaluationStatus.EVALUATING) {
			throw new DomainException(ErrorCode.CONFLICT, "Only an evaluating version can be evaluated.");
		}
		evaluationStatus = EvaluationStatus.EVALUATED;
		this.confirmedAt = confirmedAt;
	}

	public void markRejected(Instant confirmedAt) {
		ensureMutable();
		if (evaluationStatus != EvaluationStatus.EVALUATING) {
			throw new DomainException(ErrorCode.CONFLICT, "Only an evaluating version can be rejected.");
		}
		evaluationStatus = EvaluationStatus.REJECTED;
		this.confirmedAt = confirmedAt;
	}

	public void markEvaluationFailed() {
		ensureMutable();
		if (evaluationStatus != EvaluationStatus.EVALUATING) {
			throw new DomainException(ErrorCode.CONFLICT, "Only an evaluating version can fail.");
		}
		evaluationStatus = EvaluationStatus.EVALUATION_FAILED;
	}

	public boolean isEvaluationStale(Instant now, Duration timeout) {
		return evaluationStatus == EvaluationStatus.EVALUATING && evaluationStartedAt != null
				&& !now.isBefore(evaluationStartedAt.plus(timeout));
	}

	private void ensureDraft() {
		if (evaluationStatus != EvaluationStatus.DRAFT) {
			throw new DomainException(ErrorCode.CONFLICT, "Only a DRAFT version can be edited.");
		}
	}

	private void ensureMutable() {
		if (evaluationStatus == EvaluationStatus.EVALUATED || evaluationStatus == EvaluationStatus.REJECTED) {
			throw new DomainException(ErrorCode.CONFLICT, "Completed versions are immutable.");
		}
	}

	private static String normalizeContent(String content) {
		if (content == null) {
			throw new DomainException(ErrorCode.VALIDATION_ERROR, "content must not be blank.");
		}
		String normalized = content.trim();
		if (normalized.isBlank()) {
			throw new DomainException(ErrorCode.VALIDATION_ERROR, "content must not be blank.");
		}
		if (normalized.length() > 500) {
			throw new DomainException(ErrorCode.VALIDATION_ERROR, "content must be at most 500 characters.");
		}
		return normalized;
	}
}
