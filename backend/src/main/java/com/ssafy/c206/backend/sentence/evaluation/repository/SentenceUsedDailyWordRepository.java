package com.ssafy.c206.backend.sentence.evaluation.repository;

import java.util.List;

import com.ssafy.c206.backend.sentence.evaluation.domain.SentenceUsedDailyWord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SentenceUsedDailyWordRepository extends JpaRepository<SentenceUsedDailyWord, Long> {
	@Query("select used from SentenceUsedDailyWord used join fetch used.dailyWordSetItem item join fetch item.word where used.sentenceVersion.id = :sentenceVersionId order by used.id")
	List<SentenceUsedDailyWord> findDetailBySentenceVersionId(Long sentenceVersionId);

	@Query("select used from SentenceUsedDailyWord used join fetch used.dailyWordSetItem item join fetch item.word "
			+ "where used.sentenceVersion.id in :sentenceVersionIds "
			+ "order by used.sentenceVersion.id, used.id")
	List<SentenceUsedDailyWord> findDetailBySentenceVersionIdIn(@Param("sentenceVersionIds") List<Long> sentenceVersionIds);
}
