package com.ssafy.c206.backend.sentence.evaluation.repository;

import java.time.LocalDate;
import java.util.Optional;

import com.ssafy.c206.backend.sentence.evaluation.domain.UserDailyEvaluationUsage;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserDailyEvaluationUsageRepository extends JpaRepository<UserDailyEvaluationUsage, Long> {
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select usage from UserDailyEvaluationUsage usage where usage.user.id = :userId and usage.serviceDate = :serviceDate")
	Optional<UserDailyEvaluationUsage> findByUserIdAndServiceDateForUpdate(@Param("userId") Long userId,
			@Param("serviceDate") LocalDate serviceDate);

	Optional<UserDailyEvaluationUsage> findByUserIdAndServiceDate(Long userId, LocalDate serviceDate);
}
