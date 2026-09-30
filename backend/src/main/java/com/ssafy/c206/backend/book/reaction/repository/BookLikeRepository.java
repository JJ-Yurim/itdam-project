package com.ssafy.c206.backend.book.reaction.repository;

import java.util.Collection;
import java.util.List;

import com.ssafy.c206.backend.book.reaction.domain.BookLike;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface BookLikeRepository extends JpaRepository<BookLike, Long> {
	interface BookLikeCount {
		Long getBookId();

		long getLikeCount();
	}

	boolean existsByUserIdAndBookId(Long userId, Long bookId);

	long deleteByUserIdAndBookId(Long userId, Long bookId);

	long countByBookId(Long bookId);

	@Query("select bookLike.book.id as bookId, count(bookLike) as likeCount from BookLike bookLike "
			+ "where bookLike.book.id in :bookIds group by bookLike.book.id")
	List<BookLikeCount> countGroupedByBookIdIn(Collection<Long> bookIds);

	@Query("select bookLike.book.id from BookLike bookLike "
			+ "where bookLike.user.id = :userId and bookLike.book.id in :bookIds")
	List<Long> findLikedBookIds(Long userId, Collection<Long> bookIds);
}
