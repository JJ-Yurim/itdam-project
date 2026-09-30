package com.ssafy.c206.backend.boss.repository;

import java.util.Collection;
import java.util.List;

import com.ssafy.c206.backend.boss.domain.BossMood;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Modifying;

public interface BossMoodRepository extends JpaRepository<BossMood, Long> {
	@Modifying(flushAutomatically = true)
	@Query("delete from BossMood bossMood where bossMood.boss.id = :bossId")
	void deleteByBossId(@Param("bossId") Long bossId);

	@Query("""
			select bossMood from BossMood bossMood
			join fetch bossMood.boss
			join fetch bossMood.moodCategory
			where bossMood.boss.id in :bossIds
			""")
	List<BossMood> findDetailsByBossIdIn(@Param("bossIds") Collection<Long> bossIds);
}
