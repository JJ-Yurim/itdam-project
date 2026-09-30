package com.ssafy.c206.backend.book.feed.repository;

import java.time.LocalDate;
import java.util.Optional;

import com.ssafy.c206.backend.book.feed.domain.WeeklySentenceSet;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WeeklySentenceSetRepository extends JpaRepository<WeeklySentenceSet, Long> {
	Optional<WeeklySentenceSet> findByWeekStartDate(LocalDate weekStartDate);
}
