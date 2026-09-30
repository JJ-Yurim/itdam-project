package com.ssafy.c206.backend.word.service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.random.RandomGenerator;

import com.ssafy.c206.backend.common.api.DomainException;
import com.ssafy.c206.backend.common.api.ErrorCode;
import com.ssafy.c206.backend.word.domain.Word;
import com.ssafy.c206.backend.word.repository.WordRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

/** Selects five active words without materializing the complete dictionary. */
@Component
@RequiredArgsConstructor
public class ActiveWordSelector {

	private static final int DAILY_WORD_COUNT = 5;

	private final WordRepository wordRepository;

	public List<Word> selectFive() {
		return selectFive(ThreadLocalRandom.current());
	}

	/**
	 * 같은 시드의 RandomGenerator는 같은 활성 단어 집합에서 항상 같은 5개를 고른다.
	 * 비회원 미리보기가 서비스 날짜를 시드로 써서 하루 동안 동일한 결과를 보장하는 데 쓴다.
	 * 이 결정성은 pick의 오프셋 조회가 id 정렬(Sort.by("id"))로 안정적이라는 전제 위에
	 * 성립한다. 리포지토리 조회에서 정렬을 제거하면 시드가 같아도 결과가 달라진다.
	 */
	public List<Word> selectFive(RandomGenerator random) {
		long activeCount = wordRepository.countByIsActiveTrue();
		if (activeCount < DAILY_WORD_COUNT) {
			throw new DomainException(ErrorCode.CONFLICT, "At least five active words are required.");
		}
		List<Word> selected = new ArrayList<>(DAILY_WORD_COUNT);
		Set<Long> selectedIds = new HashSet<>();
		int attempts = 0;
		while (selected.size() < DAILY_WORD_COUNT && attempts++ < DAILY_WORD_COUNT * 20) {
			int offset = random.nextInt((int) activeCount);
			pick(offset, selected, selectedIds);
		}
		// Deterministic bounded fallback for random collisions.
		for (int offset = 0; selected.size() < DAILY_WORD_COUNT && offset < activeCount; offset++) {
			pick(offset, selected, selectedIds);
		}
		return selected;
	}

	private void pick(int offset, List<Word> selected, Set<Long> selectedIds) {
		wordRepository.findByIsActiveTrue(PageRequest.of(offset, 1, Sort.by("id"))).stream()
				.findFirst().ifPresent(word -> {
					if (selectedIds.add(word.getId())) selected.add(word);
				});
	}
}
