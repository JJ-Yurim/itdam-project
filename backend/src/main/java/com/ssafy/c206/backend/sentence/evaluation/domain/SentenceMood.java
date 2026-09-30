package com.ssafy.c206.backend.sentence.evaluation.domain;

import java.math.BigDecimal;

import com.ssafy.c206.backend.sentence.domain.SentenceVersion;
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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "sentence_moods", uniqueConstraints = {
		@UniqueConstraint(name = "uk_sentence_mood_category", columnNames = {"sentence_version_id", "mood_category_id"}),
		@UniqueConstraint(name = "uk_sentence_mood_rank", columnNames = {"sentence_version_id", "rank"})
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SentenceMood {
	@Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
	@ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "sentence_version_id", nullable = false) private SentenceVersion sentenceVersion;
	@ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "mood_category_id", nullable = false) private MoodCategory moodCategory;
	@Column(nullable = false, precision = 4, scale = 3) private BigDecimal strength;
	@JdbcTypeCode(SqlTypes.SMALLINT) @Column(nullable = false, columnDefinition = "smallint") private int rank;

	private SentenceMood(SentenceVersion sentenceVersion, MoodCategory moodCategory, BigDecimal strength, int rank) {
		this.sentenceVersion = sentenceVersion; this.moodCategory = moodCategory; this.strength = strength; this.rank = rank;
	}
	public static SentenceMood of(SentenceVersion version, MoodCategory category, BigDecimal strength, int rank) {
		return new SentenceMood(version, category, strength, rank);
	}
}
