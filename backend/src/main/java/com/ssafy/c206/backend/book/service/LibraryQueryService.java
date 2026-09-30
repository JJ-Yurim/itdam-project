package com.ssafy.c206.backend.book.service;

import java.util.Comparator;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.ssafy.c206.backend.book.api.BookContentResponse;
import com.ssafy.c206.backend.book.api.LibraryBookDetailResponse;
import com.ssafy.c206.backend.common.api.PageResponse;
import com.ssafy.c206.backend.book.api.LibraryBookSummaryResponse;
import com.ssafy.c206.backend.book.domain.Book;
import com.ssafy.c206.backend.book.domain.BookStatus;
import com.ssafy.c206.backend.book.domain.BookVisibility;
import com.ssafy.c206.backend.book.reaction.repository.BookBookmarkRepository;
import com.ssafy.c206.backend.book.reaction.repository.BookLikeRepository;
import com.ssafy.c206.backend.book.repository.BookContentRepository;
import com.ssafy.c206.backend.book.repository.BookRepository;
import com.ssafy.c206.backend.boss.image.service.BossImageUrlResolver;
import com.ssafy.c206.backend.common.api.DomainException;
import com.ssafy.c206.backend.common.api.ErrorCode;
import com.ssafy.c206.backend.common.security.AuthenticatedUser;
import com.ssafy.c206.backend.common.security.AuthenticatedUsers;
import com.ssafy.c206.backend.sentence.evaluation.domain.SentenceMood;
import com.ssafy.c206.backend.sentence.evaluation.domain.SentenceUsedDailyWord;
import com.ssafy.c206.backend.sentence.evaluation.service.EvaluatedSentenceQueryService;
import com.ssafy.c206.backend.user.service.UserQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class LibraryQueryService {
	private final BookRepository bookRepository;
	private final BookContentRepository contentRepository;
	private final BookLikeRepository likeRepository;
	private final BookBookmarkRepository bookmarkRepository;
	private final UserQueryService userQueryService;
	private final BossImageUrlResolver imageUrlResolver;
	private final EvaluatedSentenceQueryService evaluatedSentenceQueryService;

	@Transactional(readOnly = true)
	public PageResponse<LibraryBookSummaryResponse> list(AuthenticatedUser principal, int page, int size) {
		Long memberUserId = resolveMemberUserId(principal);
		var result = bookRepository.findLibrarySummaries(BookStatus.COMPLETED, BookVisibility.PUBLIC,
				PageRequest.of(page, size));
		List<Long> bookIds = result.getContent().stream().map(summary -> summary.getBookId()).toList();
		// 목록은 개수만 필요하다. 본문 전체를 적재하는 상세용 쿼리 대신 집계로 센다.
		Map<Long, Long> counts = bookIds.isEmpty() ? Collections.emptyMap()
				: contentRepository.countGroupedByBookIdIn(bookIds).stream()
						.collect(Collectors.toMap(BookContentRepository.BookContentCount::getBookId,
								BookContentRepository.BookContentCount::getContentCount));
		Map<Long, Long> likeCounts = bookIds.isEmpty() ? Collections.emptyMap()
				: likeRepository.countGroupedByBookIdIn(bookIds).stream()
						.collect(Collectors.toMap(BookLikeRepository.BookLikeCount::getBookId,
								BookLikeRepository.BookLikeCount::getLikeCount));
		Set<Long> likedBookIds = memberUserId == null || bookIds.isEmpty() ? Set.of()
				: Set.copyOf(likeRepository.findLikedBookIds(memberUserId, bookIds));
		Set<Long> bookmarkedBookIds = memberUserId == null || bookIds.isEmpty() ? Set.of()
				: Set.copyOf(bookmarkRepository.findBookmarkedBookIds(memberUserId, bookIds));
		List<LibraryBookSummaryResponse> items = result.getContent().stream()
				.map(summary -> new LibraryBookSummaryResponse(summary.getBookId(), summary.getBossRunId(),
						summary.getBossName(), imageUrlResolver.publicUrlForStorageKeyOrNull(summary.getCoverStorageKey()),
						summary.getCompletedAt(), counts.getOrDefault(summary.getBookId(), 0L).intValue(),
						summary.getSummary(), likeCounts.getOrDefault(summary.getBookId(), 0L),
						likedBookIds.contains(summary.getBookId()), bookmarkedBookIds.contains(summary.getBookId())))
				.toList();
		return new PageResponse<>(items, result.getNumber(), result.getSize(), result.getTotalElements(),
				result.getTotalPages(), result.hasNext());
	}

	@Transactional(readOnly = true)
	public LibraryBookDetailResponse detail(AuthenticatedUser principal, Long bookId) {
		Long memberUserId = resolveMemberUserId(principal);
		boolean anonymous = memberUserId == null;
		var book = bookRepository.findById(bookId).filter(value -> value.getStatus() == BookStatus.COMPLETED)
				.filter(value -> value.getVisibility() == BookVisibility.PUBLIC)
				.orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND));
		var bookContents = contentRepository.findDetailsByBookIdIn(List.of(bookId)).stream()
				.sorted(Comparator.comparingInt(content -> content.getSequenceNo())).toList();
		List<Long> sentenceVersionIds = bookContents.stream()
				.filter(content -> content.getSourceSubmission() != null)
				.map(content -> content.getSourceSubmission().getSentenceVersion().getId())
				.distinct().toList();
		Map<Long, List<SentenceMood>> moodsByVersion = sentenceVersionIds.isEmpty() ? Map.of()
				: evaluatedSentenceQueryService.getMoodsForVersions(sentenceVersionIds).stream()
						.collect(Collectors.groupingBy(mood -> mood.getSentenceVersion().getId()));
		Map<Long, List<SentenceUsedDailyWord>> wordsByVersion = sentenceVersionIds.isEmpty() ? Map.of()
				: evaluatedSentenceQueryService.getUsedDailyWordsForVersions(sentenceVersionIds).stream()
						.collect(Collectors.groupingBy(word -> word.getSentenceVersion().getId()));
		List<BookContentResponse> contents = bookContents.stream().map(content -> {
			Long versionId = content.getSourceSubmission() == null ? null
					: content.getSourceSubmission().getSentenceVersion().getId();
			return BookContentResponse.from(content, !anonymous,
					versionId == null ? List.of() : moodsByVersion.getOrDefault(versionId, List.of()),
					versionId == null ? List.of() : wordsByVersion.getOrDefault(versionId, List.of()));
		}).toList();
		long likeCount = likeRepository.countByBookId(bookId);
		boolean liked = memberUserId != null && likeRepository.existsByUserIdAndBookId(memberUserId, bookId);
		boolean bookmarked = memberUserId != null && bookmarkRepository.existsByUserIdAndBookId(memberUserId, bookId);
		return LibraryBookDetailResponse.from(book, contents, coverUrl(book), likeCount, liked, bookmarked);
	}

	private String coverUrl(Book book) {
		return imageUrlResolver.publicUrlOrNull(book.getCoverImageAsset());
	}

	/**
	 * 도서관 열람은 비회원을 허용한다(ISSUE-116). principal이 없으면 익명(null)으로 보고,
	 * 익명 상세 응답에서는 기여자 userId를 감춘다. 내부 식별자를 비회원에게까지 열면
	 * 사용자 열거에 노출되기 때문이다. 닉네임은 작성자 출처 표기(정책 §11.5)로 유지한다.
	 * 세션이 있으면 기존대로 활성 계정만 통과시키고, 인증 객체에 userId가 없는 비정상
	 * 상태는 익명으로 흡수하지 않고 requireUserId로 401을 드러낸다.
	 */
	private Long resolveMemberUserId(AuthenticatedUser principal) {
		if (principal == null) {
			return null;
		}
		long userId = AuthenticatedUsers.requireUserId(principal);
		userQueryService.getActiveUser(userId);
		return userId;
	}
}
