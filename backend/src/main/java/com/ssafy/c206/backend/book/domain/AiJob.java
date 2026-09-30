package com.ssafy.c206.backend.book.domain;

import java.time.Instant;
import java.util.UUID;

import com.ssafy.c206.backend.boss.domain.BossRun;
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
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "ai_jobs", indexes = @Index(name = "idx_ai_jobs_status", columnList = "status"),
		uniqueConstraints = @UniqueConstraint(name = "uk_ai_jobs_run_type", columnNames = {"boss_run_id", "job_type"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AiJob {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "boss_run_id", nullable = false)
	private BossRun bossRun;

	@Enumerated(EnumType.STRING)
	@Column(name = "job_type", nullable = false, length = 30)
	private AiJobType jobType;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private AiJobStatus status;

	@Column(nullable = false)
	private int attemptCount;

	@Column(nullable = false, updatable = false)
	private Instant createdAt;

	@Column(nullable = false)
	private Instant updatedAt;

	@Column
	private Instant requestedAt;

	@Column(name = "request_dispatched", nullable = false)
	private boolean requestDispatched;

	@Column(name = "correlation_id", nullable = false, unique = true, length = 36, updatable = false)
	private String correlationId;

	@Column(name = "model_name", length = 150)
	private String modelName;

	@Column(name = "prompt_version", length = 100)
	private String promptVersion;

	@Column(length = 500)
	private String lastError;

	private AiJob(BossRun bossRun, Instant now) {
		if (bossRun == null || now == null) {
			throw new DomainException(ErrorCode.VALIDATION_ERROR, "bossRun and time are required.");
		}
		this.bossRun = bossRun;
		this.jobType = AiJobType.BOOK_GENERATION;
		this.status = AiJobStatus.PENDING;
		this.createdAt = now;
		this.updatedAt = now;
		this.requestedAt = now;
		this.requestDispatched = false;
		this.correlationId = UUID.randomUUID().toString();
	}

	public static AiJob pending(BossRun bossRun, Instant now) {
		return new AiJob(bossRun, now);
	}

	public void markProcessing(Instant now) {
		transition(AiJobStatus.PROCESSING, now, null);
	}

	public void markRetrying(Instant now, String error) {
		transition(AiJobStatus.RETRYING, now, error);
	}

	public void markSuccess(Instant now, String modelName, String promptVersion) {
		if (modelName == null || modelName.isBlank() || promptVersion == null || promptVersion.isBlank()) {
			throw new DomainException(ErrorCode.VALIDATION_ERROR, "Book generation model metadata is required.");
		}
		this.modelName = modelName;
		this.promptVersion = promptVersion;
		transition(AiJobStatus.SUCCESS, now, null);
	}

	public void markFailed(Instant now, String error) {
		transition(AiJobStatus.FAILED, now, error);
	}

	public void markRequestDispatched(Instant now) {
		if (now == null) {
			throw new DomainException(ErrorCode.VALIDATION_ERROR, "time is required.");
		}
		requestDispatched = true;
		updatedAt = now;
	}

	private void transition(AiJobStatus next, Instant now, String error) {
		if (now == null) {
			throw new DomainException(ErrorCode.VALIDATION_ERROR, "time is required.");
		}
		status = next;
		updatedAt = now;
		lastError = error;
		if (next == AiJobStatus.PROCESSING || next == AiJobStatus.RETRYING) {
			attemptCount++;
		}
	}
}
