package com.ssafy.c206.backend.boss.image.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import com.ssafy.c206.backend.boss.image.domain.ImageAsset;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ImageAssetRepository extends JpaRepository<ImageAsset, Long> {

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select asset from ImageAsset asset where asset.id = :id")
	Optional<ImageAsset> findByIdForUpdate(@Param("id") Long id);

	@Query("""
			select asset.id from ImageAsset asset
			where (asset.status = com.ssafy.c206.backend.boss.image.domain.ImageAssetStatus.TEMPORARY
			       and asset.createdAt < :temporaryCutoff)
			   or (asset.status = com.ssafy.c206.backend.boss.image.domain.ImageAssetStatus.ACTIVE
			       and asset.createdAt < :activeCutoff)
			order by asset.id
			""")
	List<Long> findCleanupCandidateIds(@Param("temporaryCutoff") Instant temporaryCutoff,
			@Param("activeCutoff") Instant activeCutoff);
}
