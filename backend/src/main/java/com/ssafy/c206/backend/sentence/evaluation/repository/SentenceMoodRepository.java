package com.ssafy.c206.backend.sentence.evaluation.repository;

import java.util.List;

import com.ssafy.c206.backend.sentence.evaluation.domain.SentenceMood;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SentenceMoodRepository extends JpaRepository<SentenceMood, Long> {
	@Query("select mood from SentenceMood mood join fetch mood.moodCategory where mood.sentenceVersion.id = :sentenceVersionId order by mood.rank")
	List<SentenceMood> findBySentenceVersionIdOrderByRankAsc(Long sentenceVersionId);

	@Query("select mood from SentenceMood mood join fetch mood.moodCategory "
			+ "where mood.sentenceVersion.id in :sentenceVersionIds "
			+ "order by mood.sentenceVersion.id, mood.rank")
	List<SentenceMood> findDetailBySentenceVersionIdIn(@Param("sentenceVersionIds") List<Long> sentenceVersionIds);
}
