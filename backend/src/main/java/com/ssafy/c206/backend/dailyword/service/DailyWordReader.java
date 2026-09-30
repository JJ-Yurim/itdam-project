package com.ssafy.c206.backend.dailyword.service;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import com.ssafy.c206.backend.dailyword.domain.DailyWordSet;
import com.ssafy.c206.backend.dailyword.domain.DailyWordSetItem;
import com.ssafy.c206.backend.dailyword.repository.DailyWordSetItemRepository;
import com.ssafy.c206.backend.dailyword.repository.DailyWordSetRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** 다른 도메인(문장 평가 등)이 오늘의 단어를 조회할 때 사용하는 경계 서비스. 호출자의 트랜잭션에 참여한다. */
@Service
@RequiredArgsConstructor
public class DailyWordReader {

	private final DailyWordSetRepository setRepository;
	private final DailyWordSetItemRepository itemRepository;

	public Optional<DailyWordSet> findSet(Long userId, LocalDate serviceDate) {
		return setRepository.findByUserIdAndServiceDate(userId, serviceDate);
	}

	public List<DailyWordSetItem> findItems(Long dailyWordSetId) {
		return itemRepository.findByDailyWordSetIdOrderByPositionAsc(dailyWordSetId);
	}

	public Optional<DailyWordSetItem> findItem(Long itemId) {
		return itemRepository.findById(itemId);
	}
}
