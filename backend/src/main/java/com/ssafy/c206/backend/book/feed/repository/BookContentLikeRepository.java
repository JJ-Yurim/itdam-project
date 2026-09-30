package com.ssafy.c206.backend.book.feed.repository;

import java.util.Collection;
import java.util.List;

import com.ssafy.c206.backend.book.feed.domain.BookContentLike;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface BookContentLikeRepository extends JpaRepository<BookContentLike, Long> {
	interface BookContentLikeCount {
		Long getBookContentId();

		long getLikeCount();
	}

	boolean existsByUserIdAndBookContentId(Long userId, Long bookContentId);

	long deleteByUserIdAndBookContentId(Long userId, Long bookContentId);

	@Query("select contentLike.bookContent.id as bookContentId, count(contentLike) as likeCount "
			+ "from BookContentLike contentLike where contentLike.bookContent.id in :bookContentIds "
			+ "group by contentLike.bookContent.id")
	List<BookContentLikeCount> countGroupedByBookContentIdIn(Collection<Long> bookContentIds);

	@Query("select contentLike.bookContent.id from BookContentLike contentLike "
			+ "where contentLike.user.id = :userId and contentLike.bookContent.id in :bookContentIds")
	List<Long> findLikedBookContentIds(Long userId, Collection<Long> bookContentIds);
}
