package com.ssafy.c206.backend.sentence.domain;

import java.time.Instant;

import com.ssafy.c206.backend.user.domain.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "sentences")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Sentence {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "user_id", nullable = false)
	private User user;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 30)
	private SentenceStatus status;

	@Column(nullable = false, updatable = false)
	private Instant createdAt;

	@Column(nullable = false)
	private Instant updatedAt;

	@Column
	private Instant deletedAt;

	private Sentence(User user, Instant now) {
		this.user = user;
		this.status = SentenceStatus.ACTIVE;
		this.createdAt = now;
		this.updatedAt = now;
	}

	public static Sentence create(User user, Instant now) {
		return new Sentence(user, now);
	}

	public boolean isActive() {
		return status == SentenceStatus.ACTIVE && deletedAt == null;
	}

	public boolean belongsTo(Long userId) {
		return user != null && user.getId() != null && user.getId().equals(userId);
	}

	public void delete(Instant now) {
		if (deletedAt == null) {
			deletedAt = now;
			status = SentenceStatus.DELETED;
			updatedAt = now;
		}
	}
}
