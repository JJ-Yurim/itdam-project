package com.ssafy.c206.backend.book.reaction.repository;

import java.util.Collection;
import java.util.List;

import com.ssafy.c206.backend.book.domain.BookVisibility;
import com.ssafy.c206.backend.book.reaction.domain.BookBookmark;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface BookBookmarkRepository extends JpaRepository<BookBookmark, Long> {

	boolean existsByUserIdAndBookId(Long userId, Long bookId);

	long deleteByUserIdAndBookId(Long userId, Long bookId);

	/** 내 북마크 목록에서도 숨김 처리된 책은 감춘다. 북마크 행 자체는 보존한다. */
	@EntityGraph(attributePaths = {"book", "book.bossRun", "book.bossRun.boss", "book.coverImageAsset"})
	@Query("select bookmark from BookBookmark bookmark "
			+ "where bookmark.user.id = :userId and bookmark.book.visibility = :visibility "
			+ "order by bookmark.createdAt desc, bookmark.id desc")
	Page<BookBookmark> findVisibleByUserId(Long userId, BookVisibility visibility, Pageable pageable);

	@Query("select bookmark.book.id from BookBookmark bookmark "
			+ "where bookmark.user.id = :userId and bookmark.book.id in :bookIds")
	List<Long> findBookmarkedBookIds(Long userId, Collection<Long> bookIds);
}
