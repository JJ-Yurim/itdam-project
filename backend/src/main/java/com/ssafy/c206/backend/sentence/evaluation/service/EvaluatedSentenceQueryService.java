package com.ssafy.c206.backend.sentence.evaluation.service;

import java.util.Collection;
import java.util.List;

import com.ssafy.c206.backend.common.api.DomainException;
import com.ssafy.c206.backend.common.api.ErrorCode;
import com.ssafy.c206.backend.sentence.domain.EvaluationStatus;
import com.ssafy.c206.backend.sentence.domain.SentenceVersion;
import com.ssafy.c206.backend.sentence.evaluation.domain.EvaluationSafetyStatus;
import com.ssafy.c206.backend.sentence.evaluation.domain.SentenceEvaluation;
import com.ssafy.c206.backend.sentence.evaluation.domain.SentenceMood;
import com.ssafy.c206.backend.sentence.evaluation.domain.SentenceUsedDailyWord;
import com.ssafy.c206.backend.sentence.evaluation.repository.SentenceEvaluationRepository;
import com.ssafy.c206.backend.sentence.evaluation.repository.SentenceMoodRepository;
import com.ssafy.c206.backend.sentence.evaluation.repository.SentenceUsedDailyWordRepository;
import com.ssafy.c206.backend.sentence.repository.SentenceVersionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

/**
 * 다른 도메인(보스 제출 등)이 평가 완료 문장을 조회할 때 사용하는 경계 서비스.
 * sentence 내부 리포지토리·적격성 규칙은 이 서비스 뒤에 숨는다.
 */
@Service
@RequiredArgsConstructor
public class EvaluatedSentenceQueryService {

	// JPQL의 not in은 빈 목록을 허용하지 않으므로 존재하지 않는 식별자로 대체한다.
	private static final List<Long> NO_EXCLUSION = List.of(-1L);

	private final SentenceVersionRepository versionRepository;
	private final SentenceEvaluationRepository evaluationRepository;
	private final SentenceMoodRepository moodRepository;
	private final SentenceUsedDailyWordRepository usedDailyWordRepository;

	/** 문장과 함께 버전을 조회한다. 호출자의 트랜잭션에 참여한다. */
	public SentenceVersion getVersionWithSentence(long versionId) {
		return versionRepository.findWithSentenceById(versionId)
				.orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND));
	}

	/**
	 * FK 연관만 필요한 호출자를 위한 지연 참조. SELECT를 발생시키지 않으므로 짧게 유지해야 하는
	 * 쓰기 트랜잭션 안에서 쓴다. 적격성 검증이 필요하면 {@link #getEligibleEvaluation}을 먼저 쓴다.
	 *
	 * <p>호출자의 트랜잭션 안에서만 유효하다.
	 */
	public SentenceVersion getVersionReference(long versionId) {
		return versionRepository.getReferenceById(versionId);
	}

	/** 제출 적격성(소유권·평가 완료·안전성 통과)을 검증하고 평가 결과를 반환한다. */
	public SentenceEvaluation getEligibleEvaluation(long userId, SentenceVersion version) {
		if (!version.getSentence().belongsTo(userId)) {
			throw new DomainException(ErrorCode.NOT_FOUND);
		}
		if (version.getEvaluationStatus() != EvaluationStatus.EVALUATED) {
			throw new DomainException(ErrorCode.POLICY_REJECTED, "Only evaluated versions can be submitted.");
		}
		var evaluation = evaluationRepository.findBySentenceVersionId(version.getId())
				.orElseThrow(() -> new DomainException(ErrorCode.POLICY_REJECTED, "An evaluation result is required."));
		if (evaluation.getSafetyStatus() != EvaluationSafetyStatus.PASSED) {
			throw new DomainException(ErrorCode.POLICY_REJECTED, "The evaluation did not pass safety checks.");
		}
		return evaluation;
	}

	/** 버전의 무드를 랭크 순으로 반환한다. */
	public List<SentenceMood> getMoodsRanked(long versionId) {
		return moodRepository.findBySentenceVersionIdOrderByRankAsc(versionId);
	}

	/** 제외 목록(이미 제출된 버전 등)을 뺀 제출 가능 평가 페이지를 반환한다. */
	public Page<SentenceEvaluation> getEligibleForSubmission(Long userId, Collection<Long> excludedVersionIds,
			Pageable pageable) {
		Collection<Long> excluded = excludedVersionIds.isEmpty() ? NO_EXCLUSION : excludedVersionIds;
		return evaluationRepository.findEligibleByUserIdExcludingVersions(userId, excluded, pageable);
	}

	/** 보스별 예상 피해를 계산한 뒤 정렬할 수 있도록 제출 가능한 평가 전체를 반환한다. */
	public List<SentenceEvaluation> getAllEligibleForSubmission(Long userId, Collection<Long> excludedVersionIds) {
		Collection<Long> excluded = excludedVersionIds.isEmpty() ? NO_EXCLUSION : excludedVersionIds;
		return evaluationRepository.findAllEligibleByUserIdExcludingVersions(userId, excluded);
	}

	/** 페이지 이동 중 후보 전체를 다시 계산하지 않도록 후보 집합의 현재 버전을 반환한다. */
	public EligibleSentenceRevision getEligibleRevision(Long userId) {
		return evaluationRepository.findEligibleRevisionByUserId(userId);
	}

	/** 여러 버전의 무드를 한 번에 조회해 보스별 예상 피해 계산의 N+1 쿼리를 막는다. */
	public List<SentenceMood> getMoodsForVersions(List<Long> versionIds) {
		return moodRepository.findDetailBySentenceVersionIdIn(versionIds);
	}

	/** 여러 버전의 사용 제시어를 한 번에 조회해 호출자의 N+1 쿼리를 막는다. */
	public List<SentenceUsedDailyWord> getUsedDailyWordsForVersions(List<Long> versionIds) {
		return usedDailyWordRepository.findDetailBySentenceVersionIdIn(versionIds);
	}
}
