package com.ssafy.c206.backend.book.domain;

import java.time.Instant;

import com.ssafy.c206.backend.boss.submission.domain.BossSubmission;
import com.ssafy.c206.backend.common.api.DomainException;
import com.ssafy.c206.backend.common.api.ErrorCode;
import com.ssafy.c206.backend.user.domain.User;
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
@Table(name = "book_contents",
		uniqueConstraints = @UniqueConstraint(name = "uk_book_contents_book_sequence", columnNames = {"book_id", "sequence_no"}),
		indexes = @Index(name = "idx_book_contents_author_book", columnList = "author_user_id,book_id"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BookContent {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "book_id", nullable = false)
	private Book book;

	@Column(name = "sequence_no", nullable = false)
	private int sequenceNo;

	@Enumerated(EnumType.STRING)
	@Column(name = "content_type", nullable = false, length = 20)
	private ContentType contentType;

	@Column(nullable = false, length = 10_000)
	private String content;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "author_user_id")
	private User author;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "source_submission_id")
	private BossSubmission sourceSubmission;

	@Column(nullable = false, updatable = false)
	private Instant createdAt;

	private BookContent(Book book, int sequenceNo, ContentType contentType, String content, User author,
			BossSubmission sourceSubmission, Instant now) {
		if (book == null || contentType == null || now == null || sequenceNo < 1 || content == null || content.isBlank()) {
			throw new DomainException(ErrorCode.VALIDATION_ERROR, "book content fields are invalid.");
		}
		if (content.length() > 10_000) {
			throw new DomainException(ErrorCode.VALIDATION_ERROR, "book content is too long.");
		}
		if (contentType != ContentType.AI_GENERATED && author == null) {
			throw new DomainException(ErrorCode.VALIDATION_ERROR, "user-origin content requires an author.");
		}
		this.book = book;
		this.sequenceNo = sequenceNo;
		this.contentType = contentType;
		this.content = content.trim();
		this.author = author;
		this.sourceSubmission = sourceSubmission;
		this.createdAt = now;
	}

	public static BookContent create(Book book, int sequenceNo, ContentType contentType, String content, User author,
			BossSubmission sourceSubmission, Instant now) {
		return new BookContent(book, sequenceNo, contentType, content, author, sourceSubmission, now);
	}
}
