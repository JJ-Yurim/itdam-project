package com.ssafy.c206.backend.dailyword.service;

import java.util.stream.IntStream;
import java.time.LocalDate;
import java.util.List;

import com.ssafy.c206.backend.dailyword.api.DailyWordResponse;
import com.ssafy.c206.backend.dailyword.domain.DailyWordSet;
import com.ssafy.c206.backend.dailyword.domain.DailyWordSetItem;
import com.ssafy.c206.backend.dailyword.repository.DailyWordSetItemRepository;
import com.ssafy.c206.backend.dailyword.repository.DailyWordSetRepository;
import com.ssafy.c206.backend.user.domain.User;
import com.ssafy.c206.backend.word.domain.Word;
import com.ssafy.c206.backend.word.service.ActiveWordSelector;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
class DailyWordIssuanceTransaction {

	private final DailyWordSetRepository dailyWordSetRepository;
	private final DailyWordSetItemRepository dailyWordSetItemRepository;
	private final ActiveWordSelector activeWordSelector;

	@Transactional
	DailyWordResponse issue(User user, LocalDate serviceDate) {
		DailyWordSet existing = dailyWordSetRepository.findByUserIdAndServiceDate(user.getId(), serviceDate).orElse(null);
		if (existing != null) {
			return response(existing);
		}

		DailyWordSet set = dailyWordSetRepository.saveAndFlush(DailyWordSet.issue(user, serviceDate));
		List<Word> selectedWords = activeWordSelector.selectFive();
		List<DailyWordSetItem> items = IntStream.range(0, selectedWords.size())
				.mapToObj(index -> DailyWordSetItem.of(set, selectedWords.get(index), index + 1))
				.toList();
		dailyWordSetItemRepository.saveAll(items);
		dailyWordSetItemRepository.flush();
		return DailyWordResponse.from(set, items);
	}

	private DailyWordResponse response(DailyWordSet set) {
		return DailyWordResponse.from(set,
				dailyWordSetItemRepository.findByDailyWordSetIdOrderByPositionAsc(set.getId()));
	}
}
