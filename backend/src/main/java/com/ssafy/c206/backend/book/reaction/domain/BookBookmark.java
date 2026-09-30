package com.ssafy.c206.backend.book.reaction.domain;

import java.time.Instant;

import com.ssafy.c206.backend.book.domain.Book;
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
@Table(name = "book_bookmarks",
		uniqueConstraints = @UniqueConstraint(name = "uk_book_bookmark_user_book",
				columnNames = {"user_id", "book_id"}),
		indexes = @Index(name = "idx_book_bookmarks_book", columnList = "book_id"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BookBookmark {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "user_id", nullable = false)
	private User user;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "book_id", nullable = false)
	private Book book;

	@Column(nullable = false, updatable = false)
	private Instant createdAt;

	private BookBookmark(User user, Book book, Instant now) {
		if (user == null || book == null || now == null) {
			throw new DomainException(ErrorCode.VALIDATION_ERROR, "user, book and time are required.");
		}
		this.user = user;
		this.book = book;
		this.createdAt = now;
	}

	public static BookBookmark create(User user, Book book, Instant now) {
		return new BookBookmark(user, book, now);
	}
}
