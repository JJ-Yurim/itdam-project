package com.ssafy.c206.backend.postgres;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

import com.ssafy.c206.backend.integration.ai.AiEvaluationResult;
import com.ssafy.c206.backend.integration.similarity.PgVectorSentenceEmbeddingStore;
import com.ssafy.c206.backend.integration.similarity.SentenceEmbeddingStore;
import com.ssafy.c206.backend.integration.similarity.SentenceSimilarityService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 유사 문장 제출 차단(정책 §6.10)이 실제 pgvector 위에서 동작하는지 검증한다.
 * H2에는 vector 타입이 없어 이 보장은 Testcontainers에서만 얻을 수 있다.
 */
class PgVectorSimilarityIntegrationTest extends PostgresIntegrationTestSupport {

	private static final int DIMENSION = 768;
	private static final String MODEL = "text-embedding-3-small";

	@Autowired private JdbcTemplate jdbcTemplate;
	@Autowired private SentenceEmbeddingStore embeddingStore;
	@Autowired private SentenceSimilarityService similarityService;

	@Test
	void storeConstantMatchesTheActualColumnDimension() {
		// 상수가 마이그레이션과 어긋나면 저장 가드가 스키마를 잘못 기준 삼아,
		// 실패할 INSERT를 실행해 평가 트랜잭션까지 롤백시킨다.
		String columnType = jdbcTemplate.queryForObject("""
				select format_type(attribute.atttypid, attribute.atttypmod)
				from pg_attribute attribute
				join pg_class table_class on table_class.oid = attribute.attrelid
				join pg_namespace schema_namespace on schema_namespace.oid = table_class.relnamespace
				where schema_namespace.nspname = 'public'
				  and table_class.relname = 'sentence_embeddings'
				  and attribute.attname = 'embedding'
				""", String.class);

		assertThat(columnType).isEqualTo("vector(%d)".formatted(PgVectorSentenceEmbeddingStore.COLUMN_DIMENSION));
	}

	@Test
	void nearDuplicateSubmissionToTheSameRunIsBlocked() {
		long userId = insertUser();
		long bossRunId = insertBossRun();
		long submitted = insertEvaluatedVersion(userId, "이미 제출한 문장");
		long candidate = insertEvaluatedVersion(userId, "살짝 고친 문장");
		storeEmbedding(submitted, 1.0, 0.0);
		storeEmbedding(candidate, 0.99, 0.01);
		insertSubmission(bossRunId, userId, submitted);

		assertThat(similarityService.isSimilar(userId, bossRunId, candidate)).isTrue();
	}

	@Test
	void differentSentenceToTheSameRunIsAllowed() {
		long userId = insertUser();
		long bossRunId = insertBossRun();
		long submitted = insertEvaluatedVersion(userId, "이미 제출한 문장");
		long candidate = insertEvaluatedVersion(userId, "전혀 다른 문장");
		storeEmbedding(submitted, 1.0, 0.0);
		storeEmbedding(candidate, 0.0, 1.0);
		insertSubmission(bossRunId, userId, submitted);

		assertThat(similarityService.isSimilar(userId, bossRunId, candidate)).isFalse();
	}

	@Test
	void aSubmissionJustAboveTheConfiguredThresholdIsBlocked() {
		// 임계값 0.70을 고정한다(ISSUE-216). 이전 값 0.80으로 되돌아가면 이 케이스가
		// 통과해 테스트가 깨진다. 실측에서 어순만 바꾼 재제출이 0.7871, 일부만 지운 것이
		// 0.7587이었고 정상 문장의 최고값은 0.5096이었다 — 이 구간이 판정을 가르는 지점이다.
		long userId = insertUser();
		long bossRunId = insertBossRun();
		long submitted = insertEvaluatedVersion(userId, "이미 제출한 문장");
		long candidate = insertEvaluatedVersion(userId, "어순만 바꾼 문장");
		// 두 단위 벡터의 코사인이 정확히 0.75가 되도록 잡는다(0.75² + 0.6614² = 1).
		storeEmbedding(submitted, 1.0, 0.0);
		storeEmbedding(candidate, 0.75, 0.6614);
		insertSubmission(bossRunId, userId, submitted);

		assertThat(similarityService.isSimilar(userId, bossRunId, candidate)).isTrue();
	}

	@Test
	void theSameSentenceIsAllowedOnADifferentBossRun() {
		long userId = insertUser();
		long submittedRunId = insertBossRun();
		long otherRunId = insertBossRun();
		long submitted = insertEvaluatedVersion(userId, "이미 제출한 문장");
		long candidate = insertEvaluatedVersion(userId, "거의 같은 문장");
		storeEmbedding(submitted, 1.0, 0.0);
		storeEmbedding(candidate, 0.99, 0.01);
		insertSubmission(submittedRunId, userId, submitted);

		assertThat(similarityService.isSimilar(userId, submittedRunId, candidate)).isTrue();
		// 정책상 비교 범위는 동일 BossRun으로 한정한다.
		assertThat(similarityService.isSimilar(userId, otherRunId, candidate)).isFalse();
	}

	@Test
	void anotherUsersSubmissionIsNotCompared() {
		long author = insertUser();
		long otherUser = insertUser();
		long bossRunId = insertBossRun();
		long submitted = insertEvaluatedVersion(otherUser, "다른 사용자가 제출한 문장");
		long candidate = insertEvaluatedVersion(author, "거의 같은 문장");
		storeEmbedding(submitted, 1.0, 0.0);
		storeEmbedding(candidate, 0.99, 0.01);
		insertSubmission(bossRunId, otherUser, submitted);

		assertThat(similarityService.isSimilar(author, bossRunId, candidate)).isFalse();
	}

	@Test
	void sentencesWithoutAnEmbeddingAreNotBlocked() {
		long userId = insertUser();
		long bossRunId = insertBossRun();
		long submitted = insertEvaluatedVersion(userId, "임베딩 없이 평가된 문장");
		long candidate = insertEvaluatedVersion(userId, "임베딩 없는 후보 문장");
		insertSubmission(bossRunId, userId, submitted);

		// 임베딩 도입 이전에 평가된 문장이 일괄 차단되면 안 된다(fail-open).
		assertThat(similarityService.isSimilar(userId, bossRunId, candidate)).isFalse();
	}

	@Test
	void storeIgnoresResultsWithoutAnEmbedding() {
		long userId = insertUser();
		long versionId = insertEvaluatedVersion(userId, "임베딩이 없는 평가 결과");

		embeddingStore.save(versionId, evaluationResult(null), Instant.now());

		assertThat(countEmbeddings(versionId)).isZero();
	}

	@Test
	void storeSkipsVectorsWhoseDimensionDoesNotMatchTheColumn() {
		long userId = insertUser();
		long versionId = insertEvaluatedVersion(userId, "차원이 다른 임베딩");
		List<Double> wrongSize = List.of(1.0, 0.0, 0.0);

		// 저장 전에 걸러내지 않으면 SQL 오류가 평가 트랜잭션 전체를 롤백시킨다.
		embeddingStore.save(versionId, evaluationResult(wrongSize, MODEL, wrongSize.size()), Instant.now());

		assertThat(countEmbeddings(versionId)).isZero();
	}

	@Test
	void storeSkipsVectorsContainingNullElements() {
		long userId = insertUser();
		long versionId = insertEvaluatedVersion(userId, "null 원소가 섞인 임베딩");
		List<Double> withNull = new ArrayList<>(vector(1.0, 0.0));
		withNull.set(5, null);

		embeddingStore.save(versionId, evaluationResult(withNull, MODEL, DIMENSION), Instant.now());

		assertThat(countEmbeddings(versionId)).isZero();
	}

	@Test
	void storeSkipsVectorsContainingNonFiniteValues() {
		long userId = insertUser();
		long versionId = insertEvaluatedVersion(userId, "NaN이 섞인 임베딩");
		List<Double> withNaN = vector(Double.NaN, 1.0);

		// pgvector는 NaN·Infinity 원소를 거부한다.
		embeddingStore.save(versionId, evaluationResult(withNaN), Instant.now());

		assertThat(countEmbeddings(versionId)).isZero();
	}

	@Test
	void storeSkipsResultsWhoseDeclaredDimensionDisagreesWithTheVector() {
		long userId = insertUser();
		long versionId = insertEvaluatedVersion(userId, "메타데이터가 어긋난 임베딩");

		embeddingStore.save(versionId, evaluationResult(vector(1.0, 0.0), MODEL, DIMENSION + 1), Instant.now());

		assertThat(countEmbeddings(versionId)).isZero();
	}

	@Test
	void storeSkipsZeroVectorsThatWouldPoisonCosineDistance() {
		long userId = insertUser();
		long versionId = insertEvaluatedVersion(userId, "영벡터 임베딩");

		// 영벡터가 저장되면 코사인 거리가 NaN이 되고, PostgreSQL은 NaN을 모든 값보다
		// 크게 비교해 무관한 문장까지 전부 차단된다(fail-open이 fail-closed로 뒤집힘).
		embeddingStore.save(versionId, evaluationResult(vector()), Instant.now());

		assertThat(countEmbeddings(versionId)).isZero();
	}

	@Test
	void storeSkipsVectorsThatUnderflowToZeroInFloat4() {
		long userId = insertUser();
		long versionId = insertEvaluatedVersion(userId, "float4에서 0으로 언더플로하는 임베딩");

		// double로는 0이 아니지만 vector의 원소 타입(float4)으로 변환되면 전부 0이 된다.
		// 판정을 double 기준으로 하면 이 벡터가 영벡터로 저장되어 fail-closed를 유발한다.
		embeddingStore.save(versionId, evaluationResult(vector(1e-300, 1e-300)), Instant.now());

		assertThat(countEmbeddings(versionId)).isZero();
	}

	@Test
	void storeSkipsVectorsThatOverflowFloat4() {
		long userId = insertUser();
		long versionId = insertEvaluatedVersion(userId, "float4 범위를 넘는 임베딩");

		// 저장을 시도하면 "out of range" 오류로 평가 트랜잭션까지 롤백된다.
		embeddingStore.save(versionId, evaluationResult(vector(1e39, 1.0)), Instant.now());

		assertThat(countEmbeddings(versionId)).isZero();
	}

	@Test
	void storeSkipsVectorsWhoseSquaredNormUnderflowsEvenThoughElementsSurvive() {
		long userId = insertUser();
		long versionId = insertEvaluatedVersion(userId, "제곱합이 언더플로하는 임베딩");

		// 원소 1e-25는 float4로 멀쩡히 표현되지만 제곱(1e-50)이 float에서 0이 되어
		// norm이 0으로 계산된다. 그러면 코사인 유사도가 1로 클램프되어 거리가 0이 되고,
		// 무관한 문장까지 "완전히 동일"로 판정돼 전부 차단된다(fail-closed).
		embeddingStore.save(versionId, evaluationResult(vector(1e-25, 1e-25)), Instant.now());

		assertThat(countEmbeddings(versionId)).isZero();
	}

	@Test
	void storeKeepsVectorsWhoseNormSurvivesFloatAccumulation() {
		long userId = insertUser();
		long versionId = insertEvaluatedVersion(userId, "정상 norm 임베딩");

		// 가드가 정상 벡터까지 막아버리면 유사도 기능 전체가 조용히 꺼진다.
		embeddingStore.save(versionId, evaluationResult(vector(0.6, 0.8)), Instant.now());

		assertThat(countEmbeddings(versionId)).isEqualTo(1);
	}

	@Test
	void storeIsIdempotentForTheSameSentenceVersion() {
		long userId = insertUser();
		long versionId = insertEvaluatedVersion(userId, "중복 저장 대상 문장");

		storeEmbedding(versionId, 1.0, 0.0);
		storeEmbedding(versionId, 0.0, 1.0);

		assertThat(countEmbeddings(versionId)).isEqualTo(1);
	}

	private void storeEmbedding(long sentenceVersionId, double... leading) {
		embeddingStore.save(sentenceVersionId, evaluationResult(vector(leading)), Instant.now());
	}

	private static List<Double> vector(double... leading) {
		return IntStream.range(0, DIMENSION)
				.mapToObj(index -> index < leading.length ? leading[index] : 0.0)
				.toList();
	}

	private static AiEvaluationResult evaluationResult(List<Double> embedding) {
		return evaluationResult(embedding,
				embedding == null ? null : MODEL,
				embedding == null ? null : DIMENSION);
	}

	/** 어긋난 메타데이터를 명시적으로 넘겨 어떤 조합이 거부되는지 드러내는 오버로드. */
	private static AiEvaluationResult evaluationResult(List<Double> embedding, String model, Integer dimension) {
		return new AiEvaluationResult(null, null, null, List.of(), List.of(),
				MODEL, "prompt-v1", "rubric-v1", embedding, model, dimension);
	}

	private int countEmbeddings(long sentenceVersionId) {
		return jdbcTemplate.queryForObject(
				"select count(*) from sentence_embeddings where sentence_version_id = ?",
				Integer.class, sentenceVersionId);
	}

	private long insertUser() {
		String suffix = String.valueOf(System.nanoTime());
		return jdbcTemplate.queryForObject("""
				insert into users (email, password_hash, nickname, status, role, created_at, updated_at)
				values (?, 'hash', ?, 'ACTIVE', 'USER', current_timestamp, current_timestamp)
				returning id
				""", Long.class, "similarity-" + suffix + "@test.local", "similarity-" + suffix);
	}

	private long insertBossRun() {
		Long bossId = jdbcTemplate.queryForObject("""
				insert into bosses (name, description, status, created_at, updated_at)
				values (?, 'Similarity fixture', 'ACTIVE', current_timestamp, current_timestamp)
				returning id
				""", Long.class, "Similarity boss " + System.nanoTime());
		return jdbcTemplate.queryForObject("""
				insert into boss_runs
					(boss_id, status, current_phase_no, accumulated_damage, created_at, updated_at)
				values (?, 'SCHEDULED', 1, 0, current_timestamp, current_timestamp)
				returning id
				""", Long.class, bossId);
	}

	private long insertEvaluatedVersion(long userId, String content) {
		Long sentenceId = jdbcTemplate.queryForObject("""
				insert into sentences (user_id, status, created_at, updated_at)
				values (?, 'ACTIVE', current_timestamp, current_timestamp)
				returning id
				""", Long.class, userId);
		return jdbcTemplate.queryForObject("""
				insert into sentence_versions
					(sentence_id, version_no, content, evaluation_status, created_at)
				values (?, 1, ?, 'EVALUATED', current_timestamp)
				returning id
				""", Long.class, sentenceId, content);
	}

	private void insertSubmission(long bossRunId, long userId, long sentenceVersionId) {
		Long phaseId = jdbcTemplate.queryForObject("""
				insert into boss_phases
					(boss_run_id, phase_no, max_hp, remaining_hp, damage_multiplier, status)
				values (?, 1, 1000, 1000, 1.0, 'ACTIVE')
				returning id
				""", Long.class, bossRunId);
		jdbcTemplate.update("""
				insert into boss_submissions
					(boss_run_id, boss_phase_id, user_id, sentence_version_id, service_date,
					 ai_base_score_snapshot, word_bonus_snapshot, mood_bonus_snapshot,
					 weakness_bonus_snapshot, phase_modifier_snapshot, final_damage, created_at)
				values (?, ?, ?, ?, current_date, ?, ?, ?, ?, ?, 100, current_timestamp)
				""", bossRunId, phaseId, userId, sentenceVersionId,
				BigDecimal.TEN, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ONE);
	}
}
