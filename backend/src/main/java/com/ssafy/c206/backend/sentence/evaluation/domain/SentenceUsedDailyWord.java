package com.ssafy.c206.backend.sentence.evaluation.domain;

import java.time.Instant;

import com.ssafy.c206.backend.dailyword.domain.DailyWordSetItem;
import com.ssafy.c206.backend.sentence.domain.SentenceVersion;
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
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "sentence_used_daily_words", uniqueConstraints = @UniqueConstraint(name = "uk_sentence_used_daily_word", columnNames = {"sentence_version_id", "daily_word_set_item_id"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SentenceUsedDailyWord {
	@Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
	@ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "sentence_version_id", nullable = false) private SentenceVersion sentenceVersion;
	@ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "daily_word_set_item_id", nullable = false) private DailyWordSetItem dailyWordSetItem;
	@Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
	private SentenceUsedDailyWord(SentenceVersion version, DailyWordSetItem item) { this.sentenceVersion = version; this.dailyWordSetItem = item; }
	public static SentenceUsedDailyWord of(SentenceVersion version, DailyWordSetItem item) { return new SentenceUsedDailyWord(version, item); }
	// CONVENTIONS §7 예외: 감사 시각 기본값 (null-guard로 명시적 주입 허용).
	@PrePersist void onCreate() { if (createdAt == null) createdAt = Instant.now(); }
}
