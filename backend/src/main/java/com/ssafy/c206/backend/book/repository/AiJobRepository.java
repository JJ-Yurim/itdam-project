package com.ssafy.c206.backend.book.repository;

import java.util.List;
import java.util.Optional;

import com.ssafy.c206.backend.book.domain.AiJob;
import com.ssafy.c206.backend.book.domain.AiJobStatus;
import com.ssafy.c206.backend.book.domain.AiJobType;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AiJobRepository extends JpaRepository<AiJob, Long> {
	Optional<AiJob> findByBossRunIdAndJobType(Long bossRunId, AiJobType jobType);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select job from AiJob job join fetch job.bossRun run join fetch run.boss where job.id = :jobId")
	Optional<AiJob> findByIdForUpdate(@Param("jobId") Long jobId);

	@Query("select job.id from AiJob job where job.status = :status and job.requestDispatched = false "
			+ "order by job.createdAt asc")
	List<Long> findPendingDispatchIds(@Param("status") AiJobStatus status, Pageable pageable);
}
