package com.ssafy.c206.backend.sentence.repository;

import java.util.Optional;

import com.ssafy.c206.backend.sentence.domain.Sentence;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SentenceRepository extends JpaRepository<Sentence, Long> {

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select s from Sentence s where s.id = :sentenceId")
	Optional<Sentence> findByIdForUpdate(@Param("sentenceId") Long sentenceId);
}
