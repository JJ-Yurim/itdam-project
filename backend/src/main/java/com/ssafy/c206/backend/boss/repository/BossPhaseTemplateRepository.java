package com.ssafy.c206.backend.boss.repository;

import java.util.Collection;
import java.util.List;

import com.ssafy.c206.backend.boss.domain.BossPhaseTemplate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BossPhaseTemplateRepository extends JpaRepository<BossPhaseTemplate, Long> {

	@Query("""
			select template from BossPhaseTemplate template
			join fetch template.boss
			join fetch template.imageAsset
			where template.boss.id = :bossId
			order by template.phaseNo
			""")
	List<BossPhaseTemplate> findByBossIdOrderByPhaseNo(@Param("bossId") Long bossId);

	@Query("""
			select template from BossPhaseTemplate template
			join fetch template.boss
			join fetch template.imageAsset
			where template.boss.id in :bossIds
			order by template.boss.id, template.phaseNo
			""")
	List<BossPhaseTemplate> findDetailsByBossIdIn(@Param("bossIds") Collection<Long> bossIds);

	void deleteByBossId(Long bossId);

	boolean existsByImageAssetId(Long imageAssetId);

	boolean existsByBossIdAndImageAssetId(Long bossId, Long imageAssetId);
}
