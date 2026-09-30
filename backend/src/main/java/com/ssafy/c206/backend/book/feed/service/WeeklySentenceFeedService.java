package com.ssafy.c206.backend.book.feed.service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.ssafy.c206.backend.book.domain.BookVisibility;
import com.ssafy.c206.backend.book.feed.api.WeeklySentenceFeedItem;
import com.ssafy.c206.backend.book.feed.api.WeeklySentenceFeedResponse;
import com.ssafy.c206.backend.book.feed.domain.WeeklySentenceSet;
import com.ssafy.c206.backend.book.feed.domain.WeeklySentenceSetItem;
import com.ssafy.c206.backend.book.feed.repository.BookContentLikeRepository;
import com.ssafy.c206.backend.book.feed.repository.WeeklySentenceSetItemRepository;
import com.ssafy.c206.backend.book.feed.repository.WeeklySentenceSetRepository;
import com.ssafy.c206.backend.common.api.DomainException;
import com.ssafy.c206.backend.common.api.ErrorCode;
import com.ssafy.c206.backend.common.security.AuthenticatedUser;
import com.ssafy.c206.backend.common.security.AuthenticatedUsers;
import com.ssafy.c206.backend.common.time.ServiceClock;
import com.ssafy.c206.backend.user.service.UserQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

/**
 * 주간 선정 문장 피드 조회. 세트가 없으면 첫 조회가 지연 발급하며, 동시 발급 경합은
 * 유니크 제약 위반을 잡아 재조회로 수습한다(DailyWordService와 같은 패턴이라 자기호출
 * 트랜잭션 문제를 피하기 위해 클래스 수준 @Transactional을 두지 않는다).
 */
@Service
@RequiredArgsConstructor
public class WeeklySentenceFeedService {

	private final WeeklySentenceSetRepository setRepository;
	private final WeeklySentenceSetItemRepository itemRepository;
	private final BookContentLikeRepository likeRepository;
	private final WeeklyFeedIssuanceTransaction issuanceTransaction;
	private final UserQueryService userQueryService;
	private final ServiceClock serviceClock;

	/**
	 * 주간 베스트 문장을 조회한다. 비회원도 열람할 수 있다(ISSUE-206).
	 *
	 * <p>도서관 화면이 비회원에게 열려 있는데 그 안의 베스트 문장 탭만 막혀 있어 생긴
	 * 불일치를 없앤다. 선정된 문장 자체는 공개 콘텐츠이고, 개인화되는 값은 liked뿐이라
	 * 비회원에게는 false로 내린다.
	 */
	public WeeklySentenceFeedResponse getFeed(AuthenticatedUser principal) {
		Long userId = resolveActiveUserId(principal);

		LocalDate weekStartDate = serviceClock.today().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
		WeeklySentenceSet set = setRepository.findByWeekStartDate(weekStartDate).orElse(null);
		if (set == null) {
			set = issueOrFindAfterRace(weekStartDate);
		}

		List<WeeklySentenceSetItem> items = itemRepository.findVisibleDetailsBySetId(set.getId(),
				BookVisibility.PUBLIC);
		if (items.isEmpty()) {
			return new WeeklySentenceFeedResponse(weekStartDate, List.of());
		}

		List<Long> contentIds = items.stream().map(item -> item.getBookContent().getId()).toList();
		Map<Long, Long> likeCounts = likeRepository.countGroupedByBookContentIdIn(contentIds).stream()
				.collect(Collectors.toMap(BookContentLikeRepository.BookContentLikeCount::getBookContentId,
						BookContentLikeRepository.BookContentLikeCount::getLikeCount));
		Set<Long> likedContentIds = userId == null ? Set.of()
				: Set.copyOf(likeRepository.findLikedBookContentIds(userId, contentIds));

		List<WeeklySentenceFeedItem> feedItems = items.stream()
				.map(item -> WeeklySentenceFeedItem.from(item,
						likeCounts.getOrDefault(item.getBookContent().getId(), 0L),
						likedContentIds.contains(item.getBookContent().getId())))
				.toList();
		return new WeeklySentenceFeedResponse(weekStartDate, feedItems);
	}

	/**
	 * 세션이 없으면 null을 돌려준다. 인증 객체는 있는데 userId가 비어 있는 상태는 익명이
	 * 아니라 인증 설정 결함이므로 조용히 흡수하지 않고 401로 드러낸다.
	 */
	private Long resolveActiveUserId(AuthenticatedUser principal) {
		if (principal == null) {
			return null;
		}
		long userId = AuthenticatedUsers.requireUserId(principal);
		userQueryService.getActiveUser(userId);
		return userId;
	}

	private WeeklySentenceSet issueOrFindAfterRace(LocalDate weekStartDate) {
		try {
			return issuanceTransaction.issue(weekStartDate);
		} catch (DataIntegrityViolationException exception) {
			return setRepository.findByWeekStartDate(weekStartDate)
					.orElseThrow(() -> new DomainException(ErrorCode.CONFLICT,
							"Failed to issue the weekly sentence feed."));
		}
	}
}
