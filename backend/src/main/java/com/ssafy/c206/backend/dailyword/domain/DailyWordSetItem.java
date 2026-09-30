package com.ssafy.c206.backend.dailyword.domain;

import java.time.Instant;

import com.ssafy.c206.backend.word.domain.Word;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "daily_word_set_items", uniqueConstraints = {
	@UniqueConstraint(name = "uk_daily_word_item_set_position", columnNames = {"daily_word_set_id", "position"}),
	@UniqueConstraint(name = "uk_daily_word_item_set_word", columnNames = {"daily_word_set_id", "word_id"})
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DailyWordSetItem {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "daily_word_set_id", nullable = false)
	private DailyWordSet dailyWordSet;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "word_id", nullable = false)
	private Word word;

	@JdbcTypeCode(SqlTypes.SMALLINT)
	@Column(nullable = false, columnDefinition = "smallint")
	private int position;

	@Column(nullable = false, updatable = false)
	private Instant createdAt;

	private DailyWordSetItem(DailyWordSet dailyWordSet, Word word, int position) {
		this.dailyWordSet = dailyWordSet;
		this.word = word;
		this.position = position;
	}

	public static DailyWordSetItem of(DailyWordSet dailyWordSet, Word word, int position) {
		if (position < 1 || position > 5) {
			throw new IllegalArgumentException("position must be between 1 and 5");
		}
		return new DailyWordSetItem(dailyWordSet, word, position);
	}

	@PrePersist
	void onCreate() {
		if (createdAt == null) {
			createdAt = Instant.now();
		}
	}
}
