package com.ssafy.c206.backend.boss.repository;

import java.util.Optional;

import com.ssafy.c206.backend.boss.domain.Boss;
import com.ssafy.c206.backend.boss.domain.BossStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.query.Param;

public interface BossRepository extends JpaRepository<Boss, Long> {
	@Override
	@EntityGraph(attributePaths = "bookCoverImageAsset")
	Page<Boss> findAll(Pageable pageable);

	boolean existsByBookCoverImageAssetId(Long imageAssetId);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select boss from Boss boss where boss.id = :id")
	Optional<Boss> findByIdForUpdate(@Param("id") Long id);

	// :param is null 형태로 파라미터가 처음 등장하면 Hibernate가 타입을 추론하지 못해 PostgreSQL에서
	// bytea로 바인딩된다(lower(bytea) does not exist). 필터 사용 여부는 별도 boolean 파라미터로 전달한다.
	@EntityGraph(attributePaths = "bookCoverImageAsset")
	@Query("""
			select boss from Boss boss
			where (:filterQuery = false or lower(boss.name) like lower(concat('%', :query, '%')) escape '\\')
			  and (:filterStatus = false or boss.status = :status)
			""")
	Page<Boss> search(@Param("query") String query, @Param("filterQuery") boolean filterQuery,
			@Param("status") BossStatus status, @Param("filterStatus") boolean filterStatus, Pageable pageable);
}
