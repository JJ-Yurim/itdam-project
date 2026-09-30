package com.ssafy.c206.backend.book.feed.service;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import com.ssafy.c206.backend.book.domain.BookContent;
import com.ssafy.c206.backend.book.domain.BookStatus;
import com.ssafy.c206.backend.book.domain.BookVisibility;
import com.ssafy.c206.backend.book.domain.ContentType;
import com.ssafy.c206.backend.book.feed.domain.WeeklySentenceSet;
import com.ssafy.c206.backend.book.feed.domain.WeeklySentenceSetItem;
import com.ssafy.c206.backend.book.feed.repository.WeeklySentenceSetItemRepository;
import com.ssafy.c206.backend.book.feed.repository.WeeklySentenceSetRepository;
import com.ssafy.c206.backend.book.repository.BookContentRepository;
import com.ssafy.c206.backend.common.time.ServiceClock;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
class WeeklyFeedIssuanceTransaction {

	private final WeeklySentenceSetRepository setRepository;
	private final WeeklySentenceSetItemRepository itemRepository;
	private final BookContentRepository bookContentRepository;
	private final WeeklyFeedProperties properties;
	private final ServiceClock serviceClock;

	@Transactional
	WeeklySentenceSet issue(LocalDate weekStartDate) {
		WeeklySentenceSet existing = setRepository.findByWeekStartDate(weekStartDate).orElse(null);
		if (existing != null) {
			return existing;
		}

		Instant now = serviceClock.now();
		WeeklySentenceSet set = setRepository.saveAndFlush(WeeklySentenceSet.issue(weekStartDate, now));

		// 후보 윈도우는 조회 시각이 아니라 주 시작일 기준으로 고정한다(지난주에 완성된 책).
		Instant completedFrom = weekStartDate.minusDays(7).atStartOfDay(serviceClock.zoneId()).toInstant();
		Instant completedTo = weekStartDate.atStartOfDay(serviceClock.zoneId()).toInstant();
		List<Long> candidateIds = bookContentRepository.findWeeklyFeedCandidateIds(BookStatus.COMPLETED,
				BookVisibility.PUBLIC, completedFrom, completedTo,
				List.of(ContentType.USER_ORIGINAL, ContentType.USER_EDITED));

		List<Long> shuffledIds = new ArrayList<>(candidateIds);
		Collections.shuffle(shuffledIds);
		List<Long> pickedIds = shuffledIds.subList(0, Math.min(properties.weeklyCount(), shuffledIds.size()));
		Map<Long, BookContent> contentsById = bookContentRepository.findAllById(pickedIds).stream()
				.collect(Collectors.toMap(BookContent::getId, Function.identity()));

		List<WeeklySentenceSetItem> items = IntStream.range(0, pickedIds.size())
				.mapToObj(index -> WeeklySentenceSetItem.of(set, contentsById.get(pickedIds.get(index)),
						index + 1, now))
				.toList();
		itemRepository.saveAll(items);
		itemRepository.flush();
		return set;
	}
}
