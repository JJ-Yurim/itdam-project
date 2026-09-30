package com.ssafy.c206.backend.sentence.evaluation.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import com.ssafy.c206.backend.sentence.evaluation.domain.MoodCategory;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MoodCategoryRepository extends JpaRepository<MoodCategory, Long> {
	Optional<MoodCategory> findByCode(String code);

	List<MoodCategory> findByCodeIn(Collection<String> codes);
}
