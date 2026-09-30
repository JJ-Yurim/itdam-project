package com.ssafy.c206.backend.word.domain;

import java.time.Instant;

import com.ssafy.c206.backend.word.etl.DictionarySense;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.Index;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "words", indexes = @Index(name = "idx_words_active_id", columnList = "is_active,id"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Word {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@NotBlank
	@Size(max = 100)
	@Column(nullable = false, length = 100)
	private String word;

	@NotBlank
	@Size(max = 50)
	@Column(name = "part_of_speech", nullable = false, length = 50)
	private String partOfSpeech;

	@NotBlank
	@Column(nullable = false, columnDefinition = "text")
	private String definition;

	@Column(columnDefinition = "text")
	private String example;

	@Column(nullable = false)
	private boolean isActive = true;

	@Column(nullable = false, updatable = false)
	private Instant createdAt;

	private Word(String word, String partOfSpeech, String definition, String example) {
		this.word = word;
		this.partOfSpeech = partOfSpeech;
		this.definition = definition;
		this.example = example;
	}

	public static Word from(DictionarySense sense) {
		return new Word(sense.getWord(), sense.getPartOfSpeech(), sense.getDefinition(), sense.getExample());
	}

	@PrePersist
	void onCreate() {
		if (createdAt == null) {
			createdAt = Instant.now();
		}
	}
}
