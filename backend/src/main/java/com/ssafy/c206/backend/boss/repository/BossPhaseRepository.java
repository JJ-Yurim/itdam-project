package com.ssafy.c206.backend.boss.repository;

import java.util.Optional;
import java.util.Collection;
import java.util.List;

import com.ssafy.c206.backend.boss.domain.BossPhase;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

public interface BossPhaseRepository extends JpaRepository<BossPhase, Long> {

	@Query("""
			select phase from BossPhase phase
			join fetch phase.bossRun
			left join fetch phase.imageAsset
			where phase.bossRun.id in :bossRunIds
			""")
	List<BossPhase> findDetailsByBossRunIdIn(@Param("bossRunIds") Collection<Long> bossRunIds);

	/** 잠금 없는 현재 페이즈 조회. 임계구역 밖에서 페이즈 값을 읽는 계산 단계가 쓴다. */
	@Query("select phase from BossPhase phase where phase.bossRun.id = :bossRunId and phase.phaseNo = :phaseNo")
	Optional<BossPhase> findCurrentByBossRunId(@Param("bossRunId") Long bossRunId, @Param("phaseNo") int phaseNo);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select phase from BossPhase phase join fetch phase.bossRun where phase.bossRun.id = :bossRunId and phase.phaseNo = :phaseNo")
	Optional<BossPhase> findCurrentByBossRunIdForUpdate(@Param("bossRunId") Long bossRunId,
			@Param("phaseNo") int phaseNo);

	List<BossPhase> findByBossRunIdOrderByPhaseNoAsc(Long bossRunId);

	boolean existsByImageAssetId(Long imageAssetId);
}
