package com.ssafy.c206.backend.word.service;

import java.util.List;

import com.ssafy.c206.backend.word.domain.Word;
import com.ssafy.c206.backend.word.repository.WordRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ActiveWordQueryService {

	private final WordRepository wordRepository;

	@Transactional(readOnly = true)
	public long count() {
		return wordRepository.countByIsActiveTrue();
	}

	@Transactional(readOnly = true)
	public List<Word> findAll() {
		return wordRepository.findByIsActiveTrue();
	}
}
