package com.ssafy.c206.backend.integration.similarity;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.StringJoiner;

import com.ssafy.c206.backend.integration.ai.AiEvaluationResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Persists sentence embeddings into the pgvector column.
 *
 * <p>pgvector의 {@code vector} 타입은 JPA 기본 타입이 아니라 엔티티로 매핑하면 Hibernate
 * {@code validate}와 충돌한다. 그래서 이 경계만 네이티브 SQL로 접근한다.
 *
 * <p>이 저장은 평가 완료 트랜잭션 안에서 실행된다. PostgreSQL은 한 문장이 실패하면 트랜잭션
 * 전체가 abort되므로 호출부에서 예외를 삼켜도 커밋을 되살릴 수 없다. 따라서 저장할 수 없는
 * 값은 SQL을 실행하기 전에 걸러내고 건너뛴다(fail-open). 별도 트랜잭션으로 분리하는 방법은
 * 쓰지 않는다. 바깥 트랜잭션이 sentence_versions 행을 FOR UPDATE로 잡고 있어, 그 행을
 * 참조하는 INSERT가 FK 확인용 잠금을 기다리다 교착에 빠진다.
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "similarity", name = "client", havingValue = "pgvector")
public class PgVectorSentenceEmbeddingStore implements SentenceEmbeddingStore {

	/**
	 * sentence_embeddings.embedding의 컬럼 차원.
	 *
	 * <p>설정값으로 열지 않는다. 차원을 바꾸려면 Flyway 마이그레이션(컬럼 타입과 CHECK 제약)과
	 * 전체 재임베딩이 반드시 함께 필요하므로, 런타임에 따로 조정할 수 있게 두면 스키마와
	 * 어긋난 값이 가드를 통과해 INSERT가 실행되고 평가 트랜잭션까지 롤백된다.
	 * 마이그레이션과의 일치는 PgVectorSimilarityIntegrationTest가 실제 컬럼과 대조해 고정한다.
	 */
	public static final int COLUMN_DIMENSION = 768;

	private final JdbcTemplate jdbcTemplate;

	public PgVectorSentenceEmbeddingStore(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
		log.info("embedding_store_enabled store=pgvector column_dimension={}", COLUMN_DIMENSION);
	}

	@Override
	public void save(long sentenceVersionId, AiEvaluationResult result, Instant now) {
		if (!result.hasEmbedding()) {
			// AI Service가 임베딩을 만들지 못했거나 메타데이터가 일치하지 않는 경우다.
			// 평가는 그대로 성공 처리하고 이 문장은 이후 유사도 비교에서 제외된다.
			log.debug("embedding_absent sentence_version_id={}", sentenceVersionId);
			return;
		}
		if (!isStorable(sentenceVersionId, result)) {
			return;
		}
		// 재평가는 새 SentenceVersion을 만들므로 같은 버전에 두 번 저장될 일이 없다.
		// 재시도로 중복 호출되더라도 UNIQUE 제약을 깨지 않도록 멱등하게 처리한다.
		jdbcTemplate.update("""
				insert into sentence_embeddings
					(sentence_version_id, embedding, model_name, dimension, created_at)
				values (?, ?::vector, ?, ?, ?)
				on conflict (sentence_version_id) do nothing
				""",
				sentenceVersionId,
				toVectorLiteral(result.embedding()),
				result.embeddingModel(),
				result.embeddingDimension(),
				Timestamp.from(now));
	}

	/** vector 컬럼이 거부할 값을 미리 걸러낸다. 여기서 막지 못하면 평가 트랜잭션이 롤백된다. */
	private boolean isStorable(long sentenceVersionId, AiEvaluationResult result) {
		if (result.embeddingDimension() != COLUMN_DIMENSION) {
			// vector(N) 컬럼과 차원이 다르면 저장할 수 없다. 모델을 바꿨는데 스키마를
			// 옮기지 않은 상황이므로 평가를 깨뜨리는 대신 경고만 남긴다.
			log.warn("embedding_dimension_unsupported sentence_version_id={} expected={} received={}",
					sentenceVersionId, COLUMN_DIMENSION, result.embeddingDimension());
			return false;
		}
		// 판정은 double이 아니라 float4 변환값을 기준으로 한다. vector의 원소가 float4라서
		// double로는 유한한 값도 저장 시점에 달라진다. 실측 기준:
		//   1e39   → "out of range" 오류 (평가 트랜잭션까지 롤백)
		//   1e-300 → 조용히 0으로 언더플로 (아래 영벡터 검사가 놓치면 fail-closed로 이어짐)
		// null 원소는 hasEmbedding()이 이미 걸러낸다.
		boolean representable = result.embedding().stream()
				.allMatch(value -> Float.isFinite(value.floatValue()));
		if (!representable) {
			log.warn("embedding_not_representable sentence_version_id={}", sentenceVersionId);
			return false;
		}
		// 판정 기준은 원소가 아니라 norm이다. pgvector는 코사인 거리 계산에서 제곱합을
		// float로 누산하므로, 원소 자체는 float4로 표현되는 값이어도 제곱 단계에서
		// 언더플로하면 norm이 0이 된다. 그러면 유사도가 1로 클램프되어(dot/0 → Infinity)
		// 거리 0, 즉 "완전히 동일한 문장"으로 판정돼 무관한 문장까지 전부 차단된다.
		// 실측 기준: [1e-25,...]와 [1e-23,...]은 원소 검사는 통과하지만 거리가 0으로 나온다.
		// ai-service가 정규화 단계에서 거부하지만 다른 서비스의 방어이므로 여기서도 확인한다.
		if (!hasUsableNorm(result)) {
			log.warn("embedding_norm_unusable sentence_version_id={}", sentenceVersionId);
			return false;
		}
		return true;
	}

	/** pgvector와 같은 방식(float 누산)으로 제곱합을 구해 거리 계산이 성립하는지 본다. */
	private static boolean hasUsableNorm(AiEvaluationResult result) {
		float sumOfSquares = 0.0f;
		for (Double value : result.embedding()) {
			float element = value.floatValue();
			sumOfSquares += element * element;
		}
		// 0이면 영벡터이거나 제곱이 언더플로한 경우, 무한이면 제곱이 오버플로한 경우다.
		return sumOfSquares > 0.0f && Float.isFinite(sumOfSquares);
	}

	private static String toVectorLiteral(List<Double> embedding) {
		StringJoiner literal = new StringJoiner(",", "[", "]");
		embedding.forEach(value -> literal.add(String.valueOf(value.doubleValue())));
		return literal.toString();
	}
}
