package com.ssafy.c206.backend.word.repository;

import java.util.List;

import com.ssafy.c206.backend.word.domain.Word;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WordRepository extends JpaRepository<Word, Long> {

	long countByIsActiveTrue();

	List<Word> findByIsActiveTrue();

	List<Word> findByIsActiveTrue(Pageable pageable);
}
