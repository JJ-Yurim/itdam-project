package com.ssafy.c206.backend.book.feed.repository;

import java.util.List;

import com.ssafy.c206.backend.book.domain.BookVisibility;
import com.ssafy.c206.backend.book.feed.domain.WeeklySentenceSetItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface WeeklySentenceSetItemRepository extends JpaRepository<WeeklySentenceSetItem, Long> {

	/** 세트 발급 뒤 숨김 처리된 책의 문장은 세트를 다시 발급하지 않고 조회에서만 감춘다. */
	@Query("select item from WeeklySentenceSetItem item "
			+ "join fetch item.bookContent content join fetch content.book book "
			+ "join fetch book.bossRun run join fetch run.boss left join fetch content.author "
			+ "where item.weeklySentenceSet.id = :setId and book.visibility = :visibility "
			+ "order by item.position")
	List<WeeklySentenceSetItem> findVisibleDetailsBySetId(Long setId, BookVisibility visibility);
}
