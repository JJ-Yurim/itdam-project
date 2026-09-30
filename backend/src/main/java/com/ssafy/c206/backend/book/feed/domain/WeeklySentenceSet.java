package com.ssafy.c206.backend.book.feed.domain;

import java.time.Instant;
import java.time.LocalDate;

import com.ssafy.c206.backend.common.api.DomainException;
import com.ssafy.c206.backend.common.api.ErrorCode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 한 주 동안 노출되는 선정 문장 세트. 주(월요일 시작) 단위로 전역 1개만 존재하며,
 * 유니크 제약이 동시 발급 경합의 단일 심판이다.
 */
@Entity
@Table(name = "weekly_sentence_sets", uniqueConstraints = @UniqueConstraint(
		name = "uk_weekly_sentence_set_week", columnNames = "week_start_date"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WeeklySentenceSet {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "week_start_date", nullable = false)
	private LocalDate weekStartDate;

	@Column(nullable = false, updatable = false)
	private Instant createdAt;

	private WeeklySentenceSet(LocalDate weekStartDate, Instant now) {
		if (weekStartDate == null || now == null) {
			throw new DomainException(ErrorCode.VALIDATION_ERROR, "weekStartDate and time are required.");
		}
		this.weekStartDate = weekStartDate;
		this.createdAt = now;
	}

	public static WeeklySentenceSet issue(LocalDate weekStartDate, Instant now) {
		return new WeeklySentenceSet(weekStartDate, now);
	}
}
