package com.ssafy.c206.backend.sentence.evaluation.domain;

import java.time.Instant;
import java.time.LocalDate;

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
@Table(name = "user_daily_evaluation_usage", uniqueConstraints = @UniqueConstraint(name = "uk_user_daily_evaluation_usage", columnNames = {"user_id", "service_date"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserDailyEvaluationUsage {
	@Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
	@ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "user_id", nullable = false) private User user;
	@Column(name = "service_date", nullable = false) private LocalDate serviceDate;
	@Column(name = "used_count", nullable = false) private int usedCount;
	@Column(name = "updated_at", nullable = false) private Instant updatedAt;
	private UserDailyEvaluationUsage(User user, LocalDate serviceDate, Instant now) { this.user = user; this.serviceDate = serviceDate; this.updatedAt = now; }
	public static UserDailyEvaluationUsage start(User user, LocalDate date, Instant now) { return new UserDailyEvaluationUsage(user, date, now); }
	public void consume(int limit, Instant now) { if (usedCount >= limit) throw new EvaluationQuotaExceededException(); usedCount++; updatedAt = now; }
	public void release(Instant now) { if (usedCount > 0) { usedCount--; updatedAt = now; } }
	public static class EvaluationQuotaExceededException extends RuntimeException { }
}
