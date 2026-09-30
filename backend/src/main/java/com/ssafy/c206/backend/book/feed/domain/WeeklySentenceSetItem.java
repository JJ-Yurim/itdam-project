package com.ssafy.c206.backend.book.feed.domain;

import java.time.Instant;

import com.ssafy.c206.backend.book.domain.BookContent;
import com.ssafy.c206.backend.common.api.DomainException;
import com.ssafy.c206.backend.common.api.ErrorCode;
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
@Table(name = "weekly_sentence_set_items", uniqueConstraints = {
		@UniqueConstraint(name = "uk_weekly_sentence_item_set_position",
				columnNames = {"weekly_sentence_set_id", "position"}),
		@UniqueConstraint(name = "uk_weekly_sentence_item_set_content",
				columnNames = {"weekly_sentence_set_id", "book_content_id"})})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WeeklySentenceSetItem {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "weekly_sentence_set_id", nullable = false)
	private WeeklySentenceSet weeklySentenceSet;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "book_content_id", nullable = false)
	private BookContent bookContent;

	@Column(nullable = false)
	private int position;

	@Column(nullable = false, updatable = false)
	private Instant createdAt;

	private WeeklySentenceSetItem(WeeklySentenceSet weeklySentenceSet, BookContent bookContent, int position,
			Instant now) {
		if (weeklySentenceSet == null || bookContent == null || now == null || position < 1) {
			throw new DomainException(ErrorCode.VALIDATION_ERROR, "weekly sentence set item fields are invalid.");
		}
		this.weeklySentenceSet = weeklySentenceSet;
		this.bookContent = bookContent;
		this.position = position;
		this.createdAt = now;
	}

	public static WeeklySentenceSetItem of(WeeklySentenceSet weeklySentenceSet, BookContent bookContent, int position,
			Instant now) {
		return new WeeklySentenceSetItem(weeklySentenceSet, bookContent, position, now);
	}
}
