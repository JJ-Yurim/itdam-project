package com.ssafy.c206.backend.boss.submission.repository;

import java.time.LocalDate;
import java.util.Optional;
import java.util.Collection;
import java.util.List;

import com.ssafy.c206.backend.boss.submission.domain.BossSubmissionDailyCounter;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

public interface BossSubmissionDailyCounterRepository extends JpaRepository<BossSubmissionDailyCounter, Long> {
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select counter from BossSubmissionDailyCounter counter where counter.bossRun.id = :runId and counter.user.id = :userId and counter.serviceDate = :serviceDate")
	Optional<BossSubmissionDailyCounter> findByRunUserDateForUpdate(@Param("runId") Long runId,
			@Param("userId") Long userId, @Param("serviceDate") LocalDate serviceDate);

	Optional<BossSubmissionDailyCounter> findByBossRunIdAndUserIdAndServiceDate(Long bossRunId, Long userId,
			LocalDate serviceDate);

	long countByBossRunId(Long bossRunId);

	@Query("select counter from BossSubmissionDailyCounter counter join fetch counter.bossRun "
			+ "where counter.bossRun.id in :runIds and counter.user.id = :userId and counter.serviceDate = :serviceDate")
	List<BossSubmissionDailyCounter> findByBossRunIdInAndUserIdAndServiceDate(
			@Param("runIds") Collection<Long> bossRunIds, @Param("userId") Long userId,
			@Param("serviceDate") LocalDate serviceDate);
}
