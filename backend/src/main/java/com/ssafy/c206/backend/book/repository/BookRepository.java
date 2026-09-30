package com.ssafy.c206.backend.book.repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import com.ssafy.c206.backend.book.domain.Book;
import com.ssafy.c206.backend.book.domain.BookStatus;
import com.ssafy.c206.backend.book.domain.BookVisibility;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;
import org.springframework.data.repository.query.Param;

public interface BookRepository extends JpaRepository<Book, Long> {
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select book from Book book where book.id = :id")
	Optional<Book> findByIdForUpdate(@Param("id") Long id);

	Optional<Book> findByBossRunId(Long bossRunId);
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select book from Book book where book.bossRun.id = :bossRunId")
	Optional<Book> findByBossRunIdForUpdate(@Param("bossRunId") Long bossRunId);
	@Query("select book from Book book join fetch book.bossRun where book.bossRun.id in :bossRunIds")
	List<Book> findByBossRunIdIn(Collection<Long> bossRunIds);

	@Query("select book from Book book join fetch book.bossRun run join fetch run.boss left join fetch book.coverImageAsset where book.id in :bookIds")
	List<Book> findDetailsByIdIn(Collection<Long> bookIds);
	@Query(value = """
			select book.id as bookId,
			       run.id as bossRunId,
			       boss.name as bossName,
			       cover.storageKey as coverStorageKey,
			       book.completedAt as completedAt,
			       book.summary as summary
			from Book book
			join book.bossRun run
			join run.boss boss
			left join book.coverImageAsset cover
			where book.status = :status
			  and book.visibility = :visibility
			order by book.completedAt desc, book.id desc
			""", countQuery = "select count(book) from Book book "
			+ "where book.status = :status and book.visibility = :visibility")
	Page<LibraryBookSummaryProjection> findLibrarySummaries(@Param("status") BookStatus status,
			@Param("visibility") BookVisibility visibility, Pageable pageable);

	// :param is null 형태로 파라미터가 처음 등장하면 Hibernate가 타입을 추론하지 못해 PostgreSQL에서
	// bytea로 바인딩된다(lower(bytea) does not exist). 필터 사용 여부는 별도 boolean 파라미터로 전달한다.
	@Query(value = """
			select book from Book book
			join fetch book.bossRun run
			join fetch run.boss boss
			left join fetch book.coverImageAsset
			where (:filterQuery = false or lower(boss.name) like lower(concat('%', :query, '%')) escape '\\')
			  and (:filterStatuses = false or book.status in :statuses)
			  and (:filterVisibility = false or book.visibility = :visibility)
			""", countQuery = """
			select count(book) from Book book
			join book.bossRun run
			join run.boss boss
			where (:filterQuery = false or lower(boss.name) like lower(concat('%', :query, '%')) escape '\\')
			  and (:filterStatuses = false or book.status in :statuses)
			  and (:filterVisibility = false or book.visibility = :visibility)
			""")
	Page<Book> searchAdmin(@Param("query") String query, @Param("filterQuery") boolean filterQuery,
			@Param("filterStatuses") boolean filterStatuses,
			@Param("statuses") Collection<BookStatus> statuses,
			@Param("visibility") BookVisibility visibility,
			@Param("filterVisibility") boolean filterVisibility, Pageable pageable);

	boolean existsByCoverImageAssetId(Long imageAssetId);

	interface LibraryBookSummaryProjection {
		Long getBookId();
		Long getBossRunId();
		String getBossName();
		String getCoverStorageKey();
		Instant getCompletedAt();
		String getSummary();
	}
}
