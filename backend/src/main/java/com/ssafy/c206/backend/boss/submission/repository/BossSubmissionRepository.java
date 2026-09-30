package com.ssafy.c206.backend.boss.submission.repository;

import com.ssafy.c206.backend.boss.submission.domain.BossSubmission;
import java.util.Collection;
import java.time.Instant;
import java.util.List;
import com.ssafy.c206.backend.boss.domain.BossRunStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BossSubmissionRepository extends JpaRepository<BossSubmission, Long> {
	interface ParticipatedBossRunRow {
		Long getBossRunId();
		Instant getLastSubmittedAt();
	}

	interface UserDamageTotalRow {
		Long getUserId();
		Long getTotalDamage();
	}

	// 리더보드의 소스 오브 트루스. 캐시(Redis ZSET)는 이 집계로 언제든 재구성된다.
	@Query("select submission.user.id as userId, sum(submission.finalDamage) as totalDamage "
			+ "from BossSubmission submission where submission.bossRun.id = :bossRunId "
			+ "group by submission.user.id order by sum(submission.finalDamage) desc, submission.user.id asc")
	List<UserDamageTotalRow> aggregateDamageTotals(@Param("bossRunId") Long bossRunId);

	boolean existsByBossRunIdAndSentenceVersionId(Long bossRunId, Long sentenceVersionId);
	long countByBossRunId(Long bossRunId);

	@Query("select submission from BossSubmission submission "
			+ "join fetch submission.sentenceVersion version "
			+ "where submission.bossRun.id = :bossRunId "
			+ "order by submission.finalDamage desc, submission.id asc")
	List<BossSubmission> findBookGenerationMaterials(@Param("bossRunId") Long bossRunId, Pageable pageable);

	// BossSubmissionSummary.from이 run.boss.name까지 접근하므로 boss를 함께 적재해 N+1을 막는다.
	@Query("select submission from BossSubmission submission join fetch submission.bossRun run join fetch run.boss "
			+ "join fetch submission.bossPhase join fetch submission.sentenceVersion "
			+ "where submission.bossRun.id in :runIds and submission.user.id = :userId order by submission.createdAt desc")
	List<BossSubmission> findDetailsByBossRunIdInAndUserId(@Param("runIds") Collection<Long> runIds,
			@Param("userId") Long userId);

	Page<BossSubmission> findByBossRunIdAndUserIdOrderByCreatedAtDesc(Long bossRunId, Long userId, Pageable pageable);

	@Query(value = """
			select submission.bossRun.id as bossRunId, max(submission.createdAt) as lastSubmittedAt
			from BossSubmission submission
			where submission.user.id = :userId
			group by submission.bossRun.id
			order by max(submission.createdAt) desc, submission.bossRun.id desc
			""", countQuery = """
			select count(distinct submission.bossRun.id)
			from BossSubmission submission
			where submission.user.id = :userId
			""")
	Page<ParticipatedBossRunRow> findParticipatedBossRuns(@Param("userId") Long userId, Pageable pageable);

	@Query(value = """
			select submission.bossRun.id as bossRunId, max(submission.createdAt) as lastSubmittedAt
			from BossSubmission submission
			where submission.user.id = :userId
			  and submission.bossRun.status = :status
			group by submission.bossRun.id
			order by max(submission.createdAt) desc, submission.bossRun.id desc
			""", countQuery = """
			select count(distinct submission.bossRun.id)
			from BossSubmission submission
			where submission.user.id = :userId
			  and submission.bossRun.status = :status
			""")
	Page<ParticipatedBossRunRow> findParticipatedBossRunsByStatus(@Param("userId") Long userId,
			@Param("status") BossRunStatus status, Pageable pageable);

	@Query("select submission.sentenceVersion.id from BossSubmission submission "
			+ "where submission.bossRun.id = :bossRunId and submission.user.id = :userId")
	List<Long> findSubmittedSentenceVersionIds(@Param("bossRunId") Long bossRunId, @Param("userId") Long userId);

	@Query("select submission from BossSubmission submission join fetch submission.bossRun run join fetch run.boss "
			+ "join fetch submission.bossPhase join fetch submission.sentenceVersion where submission.sentenceVersion.id in :versionIds")
	List<BossSubmission> findDetailsBySentenceVersionIdIn(@Param("versionIds") Collection<Long> versionIds);

	@Query("select submission from BossSubmission submission join fetch submission.bossRun run join fetch run.boss "
			+ "join fetch submission.bossPhase join fetch submission.sentenceVersion where submission.sentenceVersion.id = :versionId")
	List<BossSubmission> findDetailsBySentenceVersionId(@Param("versionId") Long versionId);
}
