package com.ssafy.c206.backend.sentence.repository;

import java.util.Collection;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import com.ssafy.c206.backend.sentence.domain.SentenceVersion;
import com.ssafy.c206.backend.sentence.domain.EvaluationStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import jakarta.persistence.LockModeType;

public interface SentenceVersionRepository extends JpaRepository<SentenceVersion, Long> {

	@Query("select coalesce(max(v.versionNo), 0) from SentenceVersion v where v.sentence.id = :sentenceId")
	int findMaxVersionNo(@Param("sentenceId") Long sentenceId);

	List<SentenceVersion> findBySentenceIdOrderByVersionNoAsc(Long sentenceId);

	@EntityGraph(attributePaths = "sentence")
	Optional<SentenceVersion> findWithSentenceById(Long id);

	/**
	 * 회수 가능한 대상만 오래된 것부터 제한해서 가져온다.
	 *
	 * <p>컷오프를 쿼리에서 거르는 이유는 배치 크기가 "회수 가능한 N건"을 뜻하게 하려는
	 * 것이다. 상태만 걸러 오면 아직 진행 중인 평가가 자리를 채워 한 회차가 아무것도
	 * 회수하지 못할 수 있다. evaluationStartedAt이 비어 있는 행은 회수 판정 자체가
	 * 불가능하므로(SentenceVersion.isEvaluationStale) 이 조건에서 자연히 빠진다.
	 *
	 * <p>식별자만 조회한다. 호출자는 id만 쓰고 회수 트랜잭션이 어차피 잠금과 함께 다시
	 * 읽으므로, 여기서 엔티티를 채우면 쓰지 않을 행을 배치 크기만큼 적재하게 된다.
	 */
	@Query("select version.id from SentenceVersion version"
			+ " where version.evaluationStatus = :status and version.evaluationStartedAt <= :staleBefore"
			+ " order by version.evaluationStartedAt asc, version.id asc")
	List<Long> findRecoverableIds(@Param("status") EvaluationStatus status,
			@Param("staleBefore") Instant staleBefore, Pageable pageable);

	@Query(value = "select version from SentenceVersion version "
			+ "join fetch version.sentence sentence "
			+ "where sentence.user.id = :userId and sentence.deletedAt is null "
			+ "and version.evaluationStatus in :statuses",
			countQuery = "select count(version) from SentenceVersion version "
					+ "join version.sentence sentence "
					+ "where sentence.user.id = :userId and sentence.deletedAt is null "
					+ "and version.evaluationStatus in :statuses")
	Page<SentenceVersion> findUnevaluatedArchiveByUserId(
			@Param("userId") Long userId,
			@Param("statuses") Collection<EvaluationStatus> statuses,
			Pageable pageable);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select version from SentenceVersion version join fetch version.sentence where version.id = :versionId")
	Optional<SentenceVersion> findWithSentenceByIdForUpdate(@Param("versionId") Long versionId);

	/**
	 * 접수됐지만 브로커에 발행되지 못한 평가의 식별자. 발행 릴레이 전용으로,
	 * V16의 부분 인덱스(EVALUATING·미발행)를 그대로 탄다. 상관 ID가 없는 행은
	 * 동기 시대의 것이라 발행 대상이 아니다 — stale 회수가 정리한다.
	 *
	 * <p>acceptedBefore 컷오프는 접수 직후의 즉시 발행과 릴레이의 경합을 없앤다.
	 * 즉시 발행은 confirm 대기(최대 5초)를 마쳐야 dispatched를 찍는데, 릴레이 주기(2초)가
	 * 그보다 짧아 컷오프 없이는 아직 발행 중인 행을 릴레이가 집어 이중 발행(GMS 이중
	 * 호출)이 된다. 유예보다 오래된 행만 즉시 발행이 실패한 것으로 간주한다.
	 */
	@Query("select version.id from SentenceVersion version"
			+ " where version.evaluationStatus = :status and version.evaluationDispatched = false"
			+ " and version.evaluationCorrelationId is not null"
			+ " and version.evaluationStartedAt <= :acceptedBefore"
			+ " order by version.evaluationStartedAt asc, version.id asc")
	List<Long> findUndispatchedEvaluatingIds(@Param("status") EvaluationStatus status,
			@Param("acceptedBefore") Instant acceptedBefore, Pageable pageable);
}
