package com.ssafy.c206.backend.book.repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

import com.ssafy.c206.backend.book.domain.BookContent;
import com.ssafy.c206.backend.book.domain.BookStatus;
import com.ssafy.c206.backend.book.domain.BookVisibility;
import com.ssafy.c206.backend.book.domain.ContentType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface BookContentRepository extends JpaRepository<BookContent, Long> {
	interface ContributedBookRow {
		Long getBookId();
		Long getMyIncludedContentCount();
	}

	@Query("select content from BookContent content join fetch content.book book left join fetch content.author author "
			+ "left join fetch content.sourceSubmission submission left join fetch submission.sentenceVersion version "
			+ "where book.id in :bookIds order by book.id, content.sequenceNo")
	List<BookContent> findDetailsByBookIdIn(Collection<Long> bookIds);

	/**
	 * 도서관 목록의 콘텐츠 수 집계. 목록은 개수만 필요하므로 본문·작성자·제출까지 적재하는
	 * findDetailsByBookIdIn을 쓰지 않는다. 비회원에게 공개된 경로라 페이지당 fetch 비용이
	 * 그대로 공격면이 된다(ISSUE-116).
	 */
	@Query("select content.book.id as bookId, count(content) as contentCount from BookContent content "
			+ "where content.book.id in :bookIds group by content.book.id")
	List<BookContentCount> countGroupedByBookIdIn(Collection<Long> bookIds);

	@Query(value = """
			select content.book.id as bookId, count(content.id) as myIncludedContentCount
			from BookContent content
			where content.author.id = :userId
			  and content.book.status = :status
			  and content.book.visibility = :visibility
			group by content.book.id, content.book.completedAt
			order by content.book.completedAt desc, content.book.id desc
			""", countQuery = """
			select count(distinct content.book.id)
			from BookContent content
			where content.author.id = :userId
			  and content.book.status = :status
			  and content.book.visibility = :visibility
			""")
	Page<ContributedBookRow> findContributedCompletedBooks(Long userId, BookStatus status,
			BookVisibility visibility, Pageable pageable);

	interface BookContentCount {
		Long getBookId();

		long getContentCount();
	}

	@Query("select content from BookContent content join fetch content.book book join fetch content.sourceSubmission submission "
			+ "join fetch submission.sentenceVersion version "
			+ "where content.sourceSubmission.id in :submissionIds order by content.sequenceNo")
	List<BookContent> findBySourceSubmissionIdIn(Collection<Long> submissionIds);

	/**
	 * 주간 선정 문장 피드 후보. 윈도우를 완성 시각 기준으로 닫아 두어(지난주),
	 * 같은 주에는 누가 언제 세트를 발급해도 후보군이 동일하다.
	 */
	@Query("select content.id from BookContent content "
			+ "where content.book.status = :status "
			+ "and content.book.visibility = :visibility "
			+ "and content.book.completedAt >= :completedFrom and content.book.completedAt < :completedTo "
			+ "and content.contentType in :contentTypes")
	List<Long> findWeeklyFeedCandidateIds(BookStatus status, BookVisibility visibility,
			Instant completedFrom, Instant completedTo, Collection<ContentType> contentTypes);

	long countByBookId(Long bookId);
}
