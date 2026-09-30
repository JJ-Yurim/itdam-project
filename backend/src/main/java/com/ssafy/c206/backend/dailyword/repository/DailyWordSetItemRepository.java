package com.ssafy.c206.backend.dailyword.repository;

import java.util.List;

import com.ssafy.c206.backend.dailyword.domain.DailyWordSetItem;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DailyWordSetItemRepository extends JpaRepository<DailyWordSetItem, Long> {

	@Query("select item from DailyWordSetItem item join fetch item.word where item.dailyWordSet.id = :dailyWordSetId order by item.position asc")
	List<DailyWordSetItem> findByDailyWordSetIdOrderByPositionAsc(Long dailyWordSetId);

	long countByDailyWordSetId(Long dailyWordSetId);
}
