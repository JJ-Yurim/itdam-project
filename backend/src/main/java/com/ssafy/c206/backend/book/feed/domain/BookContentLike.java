package com.ssafy.c206.backend.book.feed.domain;

import java.time.Instant;

import com.ssafy.c206.backend.book.domain.BookContent;
import com.ssafy.c206.backend.common.api.DomainException;
import com.ssafy.c206.backend.common.api.ErrorCode;
import com.ssafy.c206.backend.user.domain.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
@Table(name = "book_content_likes",
		uniqueConstraints = @UniqueConstraint(name = "uk_book_content_like_user_content",
				columnNames = {"user_id", "book_content_id"}),
		indexes = @Index(name = "idx_book_content_likes_content", columnList = "book_content_id"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BookContentLike {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "user_id", nullable = false)
	private User user;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "book_content_id", nullable = false)
	private BookContent bookContent;

	@Column(nullable = false, updatable = false)
	private Instant createdAt;

	private BookContentLike(User user, BookContent bookContent, Instant now) {
		if (user == null || bookContent == null || now == null) {
			throw new DomainException(ErrorCode.VALIDATION_ERROR, "user, bookContent and time are required.");
		}
		this.user = user;
		this.bookContent = bookContent;
		this.createdAt = now;
	}

	public static BookContentLike create(User user, BookContent bookContent, Instant now) {
		return new BookContentLike(user, bookContent, now);
	}
}
