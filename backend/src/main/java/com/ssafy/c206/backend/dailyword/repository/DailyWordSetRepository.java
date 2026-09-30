package com.ssafy.c206.backend.dailyword.repository;

import java.time.LocalDate;
import java.util.Optional;

import com.ssafy.c206.backend.dailyword.domain.DailyWordSet;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DailyWordSetRepository extends JpaRepository<DailyWordSet, Long> {

	Optional<DailyWordSet> findByUserIdAndServiceDate(Long userId, LocalDate serviceDate);
}
