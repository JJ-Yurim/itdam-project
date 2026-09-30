package com.ssafy.c206.backend.boss.submission.domain;

import java.time.Instant;
import java.time.LocalDate;

import com.ssafy.c206.backend.boss.domain.BossRun;
import com.ssafy.c206.backend.common.api.DomainException;
import com.ssafy.c206.backend.common.api.ErrorCode;
import com.ssafy.c206.backend.user.domain.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "boss_submission_daily_counters", uniqueConstraints = @UniqueConstraint(
		name = "uk_boss_submission_counter_run_user_date", columnNames = {"boss_run_id", "user_id", "service_date"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BossSubmissionDailyCounter {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "boss_run_id", nullable = false)
	private BossRun bossRun;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "user_id", nullable = false)
	private User user;

	@Column(name = "service_date", nullable = false)
	private LocalDate serviceDate;

	@Column(name = "submission_count", nullable = false)
	private int submissionCount;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	private BossSubmissionDailyCounter(BossRun bossRun, User user, LocalDate serviceDate, Instant now) {
		this.bossRun = bossRun;
		this.user = user;
		this.serviceDate = serviceDate;
		this.submissionCount = 0;
		this.updatedAt = now;
	}

	public static BossSubmissionDailyCounter start(BossRun bossRun, User user, LocalDate serviceDate, Instant now) {
		if (bossRun == null || user == null || serviceDate == null || now == null) {
			throw new DomainException(ErrorCode.VALIDATION_ERROR, "Counter references and time are required.");
		}
		return new BossSubmissionDailyCounter(bossRun, user, serviceDate, now);
	}

	public void incrementWithin(int maximum, Instant now) {
		if (submissionCount >= maximum) {
			throw new DomainException(ErrorCode.RATE_LIMITED, "Daily boss submission limit has been reached.");
		}
		submissionCount++;
		updatedAt = now;
	}
}
