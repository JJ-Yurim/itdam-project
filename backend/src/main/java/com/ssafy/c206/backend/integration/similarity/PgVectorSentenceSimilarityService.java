package com.ssafy.c206.backend.integration.similarity;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Blocks a submission whose embedding is too close to one the same user already
 * submitted to the same boss run (정책 §6.10).
 *
 * <p>비교 대상은 정책대로 동일 사용자·동일 BossRun의 이전 제출로 한정한다. 다른 회차나
 * 다른 사용자의 문장은 비교하지 않는다.
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "similarity", name = "client", havingValue = "pgvector")
public class PgVectorSentenceSimilarityService implements SentenceSimilarityService {

	// 임계값은 임베딩 모델마다 분포가 달라 운영 중 조정이 필요하다(정책 §13.1).
	//
	// 0.70은 실측으로 정한 값이다(ISSUE-216). 기준 문장 하나에 회피성 재제출 11건과
	// 정상 문장 7건을 만들어 운영과 같은 임베딩으로 재보니, 정상 문장의 최고값이 0.5096이고
	// 회피 문장의 최저값이 0.7587이라 그 사이가 비어 있다. 임계값은 이 빈 구간에 두어야 한다.
	// 직전 값 0.80은 구간을 넘어 회피 문장 쪽에 있어서 어순만 바꾸거나(0.7871) 일부만 지운
	// (0.7587) 재제출을 놓쳤고, 실제로 같은 사용자가 4초 간격으로 두 문장을 통과시킨 기록이
	// 있다(유사도 0.7307).
	private static final String THRESHOLD_PROPERTY = "${similarity.block-threshold:0.70}";

	private final JdbcTemplate jdbcTemplate;
	private final double blockThreshold;

	public PgVectorSentenceSimilarityService(JdbcTemplate jdbcTemplate,
			@Value(THRESHOLD_PROPERTY) double blockThreshold) {
		// 코사인 유사도는 [-1, 1] 범위다. 백분율로 착각해 90을 넣으면 아무도 차단되지 않고,
		// 0 이하를 넣으면 무관한 문장까지 전부 차단된다(무작위 벡터쌍의 코사인은 0 근처다).
		// 둘 다 예외 없이 정책만 뒤집히므로 기동 시점에 막는다.
		if (blockThreshold <= 0.0 || blockThreshold > 1.0) {
			throw new IllegalArgumentException(
					"similarity.block-threshold must be within (0, 1] but was " + blockThreshold);
		}
		this.jdbcTemplate = jdbcTemplate;
		this.blockThreshold = blockThreshold;
		log.info("similarity_enabled store=pgvector block_threshold={}", blockThreshold);
	}

	@Override
	public boolean isSimilar(long userId, long bossRunId, long sentenceVersionId) {
		// 후보 또는 과거 제출에 임베딩이 없으면 조인 결과가 비어 차단하지 않는다(fail-open).
		// 임베딩 도입 이전에 평가된 문장까지 일괄 차단되는 것을 막기 위한 의도적인 선택이다.
		// 모델이 다른 벡터는 같은 공간이 아니므로 model_name이 같은 것끼리만 비교한다.
		Integer match = jdbcTemplate.query("""
				select 1
				from sentence_embeddings candidate
				join boss_submissions prior_submission
					on prior_submission.boss_run_id = ?
					and prior_submission.user_id = ?
				join sentence_embeddings prior
					on prior.sentence_version_id = prior_submission.sentence_version_id
					and prior.model_name = candidate.model_name
				where candidate.sentence_version_id = ?
					and prior.sentence_version_id <> candidate.sentence_version_id
					and (1 - (candidate.embedding <=> prior.embedding)) >= ?
				limit 1
				""",
				resultSet -> resultSet.next() ? resultSet.getInt(1) : null,
				bossRunId, userId, sentenceVersionId, blockThreshold);

		if (match != null) {
			log.info("similarity_blocked user_id={} boss_run_id={} sentence_version_id={} threshold={}",
					userId, bossRunId, sentenceVersionId, blockThreshold);
		}
		return match != null;
	}
}
