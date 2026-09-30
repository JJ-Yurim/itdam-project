package com.ssafy.c206.backend.sentence.evaluation.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "mood_categories")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MoodCategory {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Short id;

	@Column(nullable = false, unique = true, length = 30)
	private String code;

	@Column(name = "name_ko", nullable = false, length = 50)
	private String nameKo;

	@Column(name = "is_active", nullable = false)
	private boolean active = true;

	private MoodCategory(String code, String nameKo) {
		this.code = code;
		this.nameKo = nameKo;
	}

	public static MoodCategory of(String code, String nameKo) {
		return new MoodCategory(code, nameKo);
	}
}
