package com.ssafy.c206.backend.dailyword.domain;

import java.time.LocalDate;
import java.time.Instant;

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
import jakarta.persistence.PrePersist;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "daily_word_sets", uniqueConstraints = @UniqueConstraint(
	name = "uk_daily_word_set_user_date", columnNames = {"user_id", "service_date"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DailyWordSet {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "user_id", nullable = false)
	private User user;

	@Column(name = "service_date", nullable = false)
	private LocalDate serviceDate;

	@Column(nullable = false, updatable = false)
	private Instant createdAt;

	private DailyWordSet(User user, LocalDate serviceDate) {
		this.user = user;
		this.serviceDate = serviceDate;
	}

	public static DailyWordSet issue(User user, LocalDate serviceDate) {
		return new DailyWordSet(user, serviceDate);
	}

	@PrePersist
	void onCreate() {
		if (createdAt == null) {
			createdAt = Instant.now();
		}
	}
}
