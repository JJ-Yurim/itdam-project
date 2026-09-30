package com.ssafy.c206.backend.sentence.evaluation.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import com.ssafy.c206.backend.sentence.evaluation.domain.SentenceEvaluation;
import com.ssafy.c206.backend.sentence.evaluation.service.EligibleSentenceRevision;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SentenceEvaluationRepository extends JpaRepository<SentenceEvaluation, Long> {
	Optional<SentenceEvaluation> findBySentenceVersionId(Long sentenceVersionId);

	@Query(value = "select evaluation from SentenceEvaluation evaluation "
			+ "join fetch evaluation.sentenceVersion version "
			+ "join fetch version.sentence sentence "
			+ "where sentence.user.id = :userId "
			+ "and sentence.deletedAt is null "
			+ "and version.evaluationStatus = com.ssafy.c206.backend.sentence.domain.EvaluationStatus.EVALUATED",
			countQuery = "select count(evaluation) from SentenceEvaluation evaluation "
					+ "join evaluation.sentenceVersion version "
					+ "join version.sentence sentence "
					+ "where sentence.user.id = :userId "
					+ "and sentence.deletedAt is null "
					+ "and version.evaluationStatus = com.ssafy.c206.backend.sentence.domain.EvaluationStatus.EVALUATED")
	Page<SentenceEvaluation> findArchiveByUserId(@Param("userId") Long userId, Pageable pageable);

	// 제출 여부는 boss 도메인의 지식이므로, 이 쿼리는 제외 목록만 받고 BossSubmission을 참조하지 않는다.
	@Query(value = "select evaluation from SentenceEvaluation evaluation "
			+ "join fetch evaluation.sentenceVersion version "
			+ "join fetch version.sentence sentence "
			+ "where sentence.user.id = :userId and sentence.deletedAt is null "
			+ "and version.evaluationStatus = com.ssafy.c206.backend.sentence.domain.EvaluationStatus.EVALUATED "
			+ "and version.id not in :excludedVersionIds",
			countQuery = "select count(evaluation) from SentenceEvaluation evaluation "
					+ "join evaluation.sentenceVersion version join version.sentence sentence "
					+ "where sentence.user.id = :userId and sentence.deletedAt is null "
					+ "and version.evaluationStatus = com.ssafy.c206.backend.sentence.domain.EvaluationStatus.EVALUATED "
					+ "and version.id not in :excludedVersionIds")
	Page<SentenceEvaluation> findEligibleByUserIdExcludingVersions(@Param("userId") Long userId,
			@Param("excludedVersionIds") Collection<Long> excludedVersionIds, Pageable pageable);

	@Query("select evaluation from SentenceEvaluation evaluation "
			+ "join fetch evaluation.sentenceVersion version "
			+ "join fetch version.sentence sentence "
			+ "where sentence.user.id = :userId and sentence.deletedAt is null "
			+ "and version.evaluationStatus = com.ssafy.c206.backend.sentence.domain.EvaluationStatus.EVALUATED "
			+ "and version.id not in :excludedVersionIds")
	List<SentenceEvaluation> findAllEligibleByUserIdExcludingVersions(@Param("userId") Long userId,
			@Param("excludedVersionIds") Collection<Long> excludedVersionIds);

	@Query("select new com.ssafy.c206.backend.sentence.evaluation.service.EligibleSentenceRevision("
			+ "count(evaluation), max(evaluation.evaluatedAt)) "
			+ "from SentenceEvaluation evaluation "
			+ "join evaluation.sentenceVersion version "
			+ "join version.sentence sentence "
			+ "where sentence.user.id = :userId and sentence.deletedAt is null "
			+ "and version.evaluationStatus = com.ssafy.c206.backend.sentence.domain.EvaluationStatus.EVALUATED")
	EligibleSentenceRevision findEligibleRevisionByUserId(@Param("userId") Long userId);

	@Query("select evaluation from SentenceEvaluation evaluation "
			+ "join fetch evaluation.sentenceVersion version "
			+ "join fetch version.sentence sentence "
			+ "where version.id = :sentenceVersionId")
	Optional<SentenceEvaluation> findArchiveDetailBySentenceVersionId(@Param("sentenceVersionId") Long sentenceVersionId);
}
