package com.ssafy.c206.backend.book.reaction.service;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.ssafy.c206.backend.book.api.LibraryBookSummaryResponse;
import com.ssafy.c206.backend.book.domain.Book;
import com.ssafy.c206.backend.book.domain.BookVisibility;
import com.ssafy.c206.backend.book.reaction.domain.BookBookmark;
import com.ssafy.c206.backend.book.reaction.repository.BookBookmarkRepository;
import com.ssafy.c206.backend.book.reaction.repository.BookLikeRepository;
import com.ssafy.c206.backend.book.repository.BookContentRepository;
import com.ssafy.c206.backend.boss.image.service.BossImageUrlResolver;
import com.ssafy.c206.backend.common.api.PageResponse;
import com.ssafy.c206.backend.common.security.AuthenticatedUser;
import com.ssafy.c206.backend.common.security.AuthenticatedUsers;
import com.ssafy.c206.backend.user.service.UserQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MyBookmarkQueryService {

	private final BookBookmarkRepository bookmarkRepository;
	private final BookLikeRepository likeRepository;
	private final BookContentRepository contentRepository;
	private final UserQueryService userQueryService;
	private final BossImageUrlResolver imageUrlResolver;

	@Transactional(readOnly = true)
	public PageResponse<LibraryBookSummaryResponse> getMyBookmarks(AuthenticatedUser principal, int page, int size) {
		long userId = AuthenticatedUsers.requireUserId(principal);
		userQueryService.getActiveUser(userId);

		var result = bookmarkRepository.findVisibleByUserId(userId, BookVisibility.PUBLIC, PageRequest.of(page, size));
		List<Long> bookIds = result.getContent().stream().map(bookmark -> bookmark.getBook().getId()).toList();
		Map<Long, Long> contentCounts = bookIds.isEmpty() ? Collections.emptyMap()
				: contentRepository.countGroupedByBookIdIn(bookIds).stream()
						.collect(Collectors.toMap(BookContentRepository.BookContentCount::getBookId,
								BookContentRepository.BookContentCount::getContentCount));
		Map<Long, Long> likeCounts = bookIds.isEmpty() ? Collections.emptyMap()
				: likeRepository.countGroupedByBookIdIn(bookIds).stream()
						.collect(Collectors.toMap(BookLikeRepository.BookLikeCount::getBookId,
								BookLikeRepository.BookLikeCount::getLikeCount));
		Set<Long> likedBookIds = bookIds.isEmpty() ? Set.of()
				: Set.copyOf(likeRepository.findLikedBookIds(userId, bookIds));

		List<LibraryBookSummaryResponse> items = result.getContent().stream().map(BookBookmark::getBook)
				.map(book -> LibraryBookSummaryResponse.from(book,
						contentCounts.getOrDefault(book.getId(), 0L).intValue(), coverUrl(book),
						likeCounts.getOrDefault(book.getId(), 0L), likedBookIds.contains(book.getId()), true))
				.toList();
		return new PageResponse<>(items, result.getNumber(), result.getSize(), result.getTotalElements(),
				result.getTotalPages(), result.hasNext());
	}

	private String coverUrl(Book book) {
		return imageUrlResolver.publicUrlOrNull(book.getCoverImageAsset());
	}
}
