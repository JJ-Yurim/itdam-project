package com.ssafy.c206.backend.postgres;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.ResultSet;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

class PostgresSchemaIntegrationTest extends PostgresIntegrationTestSupport {

	private static final Set<String> MVP_DOMAIN_TABLES = Set.of(
			"ai_jobs", "books", "book_bookmarks", "book_contents", "book_content_likes", "book_likes",
			"bosses", "boss_moods", "boss_phases", "boss_phase_templates", "boss_runs",
			"boss_submission_daily_counters", "boss_submissions", "copyright_agreements",
			"daily_word_sets", "daily_word_set_items",
			"image_assets", "mood_categories", "sentences", "sentence_embeddings", "sentence_evaluations", "sentence_moods",
			"sentence_used_daily_words", "sentence_versions", "user_daily_evaluation_usage", "users",
			"weekly_sentence_sets", "weekly_sentence_set_items", "words");

	private static final int EMBEDDING_DIMENSION = 768;

	@Autowired private JdbcTemplate jdbcTemplate;
	@Autowired private DataSource dataSource;

	@Test
	void flywayCreatesTheCurrentMvpSchemaAndJpaValidatesIt() throws Exception {
		try (var connection = dataSource.getConnection()) {
			assertThat(connection.getMetaData().getDatabaseProductName()).isEqualToIgnoringCase("PostgreSQL");
		}

		Set<String> tables = new LinkedHashSet<>(jdbcTemplate.queryForList("""
				select table_name
				from information_schema.tables
				where table_schema = 'public' and table_type = 'BASE TABLE'
				""", String.class));
		assertThat(tables).containsAll(MVP_DOMAIN_TABLES);
		assertThat(tables).doesNotContain("boss_constraints");
		// 마이그레이션이 추가되면 이 목록도 함께 갱신한다. 여기서 막히면 배포 파이프라인이 서므로,
		// 새 마이그레이션을 올릴 때는 test뿐 아니라 infraTest도 함께 돌려야 한다.
		assertThat(jdbcTemplate.queryForList("""
				select version from flyway_schema_history where success order by installed_rank
				""", String.class)).containsExactly(
						"1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "11", "12", "13", "14", "15", "16");
	}

	@Test
	void v15BackfillsBookVisibilityAsNotNullWithHiddenDefault() {
		// 버전 목록만 고정하면 마이그레이션이 실제로 무엇을 했는지는 확인되지 않는다.
		// 공개 표면 필터가 이 컬럼에 걸려 있으므로 제약과 기본값을 함께 고정한다.
		Map<String, Object> column = jdbcTemplate.queryForMap("""
				select data_type, is_nullable, column_default
				from information_schema.columns
				where table_schema = 'public' and table_name = 'books' and column_name = 'visibility'
				""");

		assertThat(column.get("data_type")).isEqualTo("character varying");
		assertThat(column.get("is_nullable")).isEqualTo("NO");
		assertThat((String) column.get("column_default")).contains("HIDDEN");
	}

	@Test
	void v16AddsEvaluationDispatchStateWithPartialIndex() {
		// 평가 발행 릴레이(ISSUE-208)가 이 컬럼과 부분 인덱스에 의존한다.
		Map<String, Object> dispatched = jdbcTemplate.queryForMap("""
				select data_type, is_nullable, column_default
				from information_schema.columns
				where table_schema = 'public' and table_name = 'sentence_versions'
				  and column_name = 'evaluation_dispatched'
				""");
		assertThat(dispatched.get("data_type")).isEqualTo("boolean");
		assertThat(dispatched.get("is_nullable")).isEqualTo("NO");
		assertThat((String) dispatched.get("column_default")).contains("false");

		assertThat(columnType("sentence_versions", "evaluation_correlation_id")).isEqualTo("uuid");
		assertThat(isNullable("sentence_versions", "evaluation_correlation_id")).isTrue();

		String indexDefinition = jdbcTemplate.queryForObject("""
				select pg_get_indexdef(index_class.oid)
				from pg_index target_index
				join pg_class target_table on target_table.oid = target_index.indrelid
				join pg_class index_class on index_class.oid = target_index.indexrelid
				where target_table.relname = 'sentence_versions'
				  and index_class.relname = 'idx_sentence_versions_undispatched_evaluation'
				""", String.class);
		assertThat(indexDefinition).contains("WHERE");
		assertThat(indexDefinition).contains("evaluation_dispatched");
	}

	@Test
	void flywaySeedsCopyrightAgreementAndEnforcesConsentConstraints() {
		Map<String, Object> agreement = jdbcTemplate.queryForMap("""
				select id, version, title, content, active, effective_at, valid_months
				from copyright_agreements where version = '1.0'
				""");
		assertThat(agreement.get("id")).isEqualTo(1L);
		assertThat(agreement.get("title")).isEqualTo("잇담 저작권 이용 동의서");
		assertThat(agreement.get("content")).isEqualTo(
				"사용자가 작성한 문장은 AI 평가, 공동 책 생성, 도서관 및 명예의 전당 공개를 위해 저장·편집·전송될 수 있습니다. "
						+ "저작권은 사용자에게 있으며, 서비스는 위 목적 범위에서 비독점적으로 이용할 수 있습니다.");
		assertThat(agreement.get("active")).isEqualTo(true);
		assertThat(jdbcTemplate.queryForObject("""
				select effective_at from copyright_agreements where version = '1.0'
				""", OffsetDateTime.class).toInstant())
				.isEqualTo(Instant.parse("2025-12-31T15:00:00Z"));
		assertThat(agreement.get("valid_months")).isEqualTo(12);

		assertThatThrownBy(() -> jdbcTemplate.update("""
				insert into copyright_agreements
					(version, title, content, active, effective_at, valid_months, created_at)
				values (?, 'Invalid', 'Invalid', false, current_timestamp, 0, current_timestamp)
				""", "invalid-" + System.nanoTime()))
				.isInstanceOf(DataIntegrityViolationException.class);

		String suffix = String.valueOf(System.nanoTime());
		Long userId = jdbcTemplate.queryForObject("""
				insert into users (email, password_hash, nickname, status, role, created_at, updated_at)
				values (?, 'hash', ?, 'ACTIVE', 'USER', current_timestamp, current_timestamp)
				returning id
				""", Long.class, "copyright-constraint-" + suffix + "@test.local",
				"copyright-constraint-" + suffix);
		assertThatThrownBy(() -> jdbcTemplate.update("""
				update users set copyright_agreement_id = 1 where id = ?
				""", userId)).isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void preservesTheCriticalUniqueForeignKeyAndPostgresMappings() throws Exception {
		assertThat(columnType("sentence_evaluations", "word_bonus")).isEqualTo("numeric");
		assertThat(columnType("sentence_evaluations", "game_base_attack")).isEqualTo("numeric");
		assertThat(columnType("daily_word_sets", "service_date")).isEqualTo("date");
		assertThat(columnType("sentence_versions", "created_at")).isEqualTo("timestamp with time zone");
		assertThat(columnType("image_assets", "purpose")).isEqualTo("character varying");
		assertThat(isNullable("image_assets", "purpose")).isFalse();
		assertUnique("daily_word_sets", "user_id", "service_date");
		assertUnique("sentence_versions", "sentence_id", "version_no");
		assertUnique("boss_submissions", "boss_run_id", "sentence_version_id");
		assertUnique("ai_jobs", "boss_run_id", "job_type");
		assertUnique("ai_jobs", "correlation_id");
		assertThat(columnType("ai_jobs", "correlation_id")).isEqualTo("character varying");
		assertPartialUniqueIndex("boss_runs", "uk_boss_runs_one_active_per_boss", "boss_id");
		assertForeignKey("boss_phases", "boss_run_id", "boss_runs");
		assertForeignKey("boss_phases", "image_asset_id", "image_assets");
		assertForeignKey("boss_phase_templates", "boss_id", "bosses");
		assertForeignKey("boss_phase_templates", "image_asset_id", "image_assets");
		assertForeignKey("bosses", "book_cover_image_asset_id", "image_assets");
		assertForeignKey("users", "copyright_agreement_id", "copyright_agreements");
		assertForeignKey("books", "cover_image_asset_id", "image_assets");
		assertForeignKey("book_contents", "source_submission_id", "boss_submissions");
		assertIndexDefinition("boss_submissions", "idx_boss_submission_user_created_at",
				"(user_id, boss_run_id, created_at DESC)");
		assertIndexDefinition("book_contents", "idx_book_contents_author_book", "(author_user_id, book_id)");
		assertIndexDefinition("users", "idx_users_copyright_agreement_id", "(copyright_agreement_id)");
	}

	@Test
	void activeRunPartialUniqueIndexOnlyRestrictsActiveStatus() {
		Long bossId = jdbcTemplate.queryForObject("""
				insert into bosses (name, description, status, created_at, updated_at)
				values ('Partial index boss', 'Schema regression fixture', 'ACTIVE', current_timestamp, current_timestamp)
				returning id
				""", Long.class);

		for (String status : List.of("SCHEDULED", "CLEARED", "ENDED_UNCLEARED", "CLOSED")) {
			insertBossRun(bossId, status);
			insertBossRun(bossId, status);
		}

		insertBossRun(bossId, "ACTIVE");
		assertThatThrownBy(() -> insertBossRun(bossId, "ACTIVE"))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void sentenceEmbeddingTableIsWiredForSimilaritySearch() throws Exception {
		assertThat(jdbcTemplate.queryForObject("""
				select count(*) from pg_extension where extname = 'vector'
				""", Integer.class)).isEqualTo(1);
		assertThat(formattedColumnType("sentence_embeddings", "embedding"))
				.isEqualTo("vector(%d)".formatted(EMBEDDING_DIMENSION));
		// 같은 SentenceVersion에 벡터가 두 개 쌓이면 어느 쪽으로 비교할지 결정할 수 없다.
		assertUnique("sentence_embeddings", "sentence_version_id");
		assertForeignKey("sentence_embeddings", "sentence_version_id", "sentence_versions");
	}

	@Test
	void cosineDistanceRanksTheNearerEmbeddingFirst() {
		long baseVersionId = insertSentenceVersion("기준 문장");
		long nearVersionId = insertSentenceVersion("비슷한 문장");
		long farVersionId = insertSentenceVersion("다른 문장");
		insertEmbedding(baseVersionId, embeddingLiteral(1.0, 0.0));
		insertEmbedding(nearVersionId, embeddingLiteral(0.9, 0.1));
		insertEmbedding(farVersionId, embeddingLiteral(0.0, 1.0));

		// 저장된 벡터로 코사인 거리 정렬이 실제로 동작하는지 확인한다(유사도 = 1 - 거리).
		List<Long> ranked = jdbcTemplate.queryForList("""
				select sentence_version_id
				from sentence_embeddings
				where sentence_version_id in (?, ?)
				order by embedding <=> (select embedding from sentence_embeddings where sentence_version_id = ?)
				""", Long.class, nearVersionId, farVersionId, baseVersionId);

		assertThat(ranked).containsExactly(nearVersionId, farVersionId);
	}

	private String formattedColumnType(String table, String column) {
		// relname만으로 조회하면 다른 스키마의 동명 릴레이션까지 잡혀 결과가 여러 건이 될 수 있다.
		return jdbcTemplate.queryForObject("""
				select format_type(attribute.atttypid, attribute.atttypmod)
				from pg_attribute attribute
				join pg_class table_class on table_class.oid = attribute.attrelid
				join pg_namespace schema_namespace on schema_namespace.oid = table_class.relnamespace
				where schema_namespace.nspname = 'public'
				  and table_class.relname = ?
				  and attribute.attname = ?
				  and attribute.attnum > 0
				  and not attribute.attisdropped
				""", String.class, table, column);
	}

	private long insertSentenceVersion(String content) {
		String suffix = String.valueOf(System.nanoTime());
		Long userId = jdbcTemplate.queryForObject("""
				insert into users (email, password_hash, nickname, status, role, created_at, updated_at)
				values (?, 'hash', ?, 'ACTIVE', 'USER', current_timestamp, current_timestamp)
				returning id
				""", Long.class, "embedding-" + suffix + "@test.local", "embedding-" + suffix);
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

	private void insertEmbedding(long sentenceVersionId, String vector) {
		jdbcTemplate.update("""
				insert into sentence_embeddings
					(sentence_version_id, embedding, model_name, dimension, created_at)
				values (?, ?::vector, 'text-embedding-3-small', ?, current_timestamp)
				""", sentenceVersionId, vector, EMBEDDING_DIMENSION);
	}

	/** 앞자리만 지정하고 나머지는 0으로 채운 vector(768) 리터럴을 만든다. */
	private static String embeddingLiteral(double... leading) {
		StringBuilder literal = new StringBuilder("[");
		for (int index = 0; index < EMBEDDING_DIMENSION; index++) {
			if (index > 0) {
				literal.append(',');
			}
			literal.append(index < leading.length ? leading[index] : 0.0);
		}
		return literal.append(']').toString();
	}

	private String columnType(String table, String column) {
		return jdbcTemplate.queryForObject("""
				select data_type from information_schema.columns
				where table_schema = 'public' and table_name = ? and column_name = ?
				""", String.class, table, column);
	}

	private boolean isNullable(String table, String column) {
		return "YES".equals(jdbcTemplate.queryForObject("""
				select is_nullable from information_schema.columns
				where table_schema = 'public' and table_name = ? and column_name = ?
				""", String.class, table, column));
	}

	private void assertUnique(String table, String... columns) throws Exception {
		Set<List<String>> sets = new LinkedHashSet<>();
		try (var connection = dataSource.getConnection(); ResultSet resultSet = connection.getMetaData()
				.getIndexInfo(null, "public", table, true, false)) {
			Map<String, List<String>> indexes = new LinkedHashMap<>();
			while (resultSet.next()) {
				String name = resultSet.getString("INDEX_NAME");
				String column = resultSet.getString("COLUMN_NAME");
				if (name != null && column != null) {
					indexes.computeIfAbsent(name, ignored -> new ArrayList<>()).add(column);
				}
			}
			sets.addAll(indexes.values());
		}
		assertThat(sets).contains(List.of(columns));
	}

	private void assertForeignKey(String table, String column, String referencedTable) throws Exception {
		Set<String> foreignKeys = new LinkedHashSet<>();
		try (var connection = dataSource.getConnection(); ResultSet resultSet = connection.getMetaData()
				.getImportedKeys(null, "public", table)) {
			while (resultSet.next()) {
				foreignKeys.add(resultSet.getString("FKCOLUMN_NAME") + "->" + resultSet.getString("PKTABLE_NAME"));
			}
		}
		assertThat(foreignKeys).contains(column + "->" + referencedTable);
	}

	private void assertIndexDefinition(String table, String index, String expectedColumns) {
		String definition = jdbcTemplate.queryForObject("""
				select pg_get_indexdef(index_class.oid)
				from pg_index target_index
				join pg_class target_table on target_table.oid = target_index.indrelid
				join pg_class index_class on index_class.oid = target_index.indexrelid
				where target_table.relname = ? and index_class.relname = ?
				""", String.class, table, index);
		assertThat(definition).contains(expectedColumns);
	}

	private void assertPartialUniqueIndex(String table, String index, String column) {
		var definition = jdbcTemplate.queryForMap("""
				select target_index.indisunique as is_unique,
				       pg_get_indexdef(target_index.indexrelid) as definition,
				       pg_get_expr(target_index.indpred, target_index.indrelid) as predicate
				from pg_index target_index
				join pg_class target_table on target_table.oid = target_index.indrelid
				join pg_class index_class on index_class.oid = target_index.indexrelid
				where target_table.relname = ? and index_class.relname = ?
				""", table, index);
		assertThat(definition.get("is_unique")).isEqualTo(true);
		assertThat(String.valueOf(definition.get("definition"))).contains(column);
		assertThat(definition.get("predicate")).isNotNull();
	}

	private void insertBossRun(Long bossId, String status) {
		jdbcTemplate.update("""
				insert into boss_runs
					(boss_id, status, current_phase_no, accumulated_damage, created_at, updated_at)
				values (?, ?, 1, 0, current_timestamp, current_timestamp)
				""", bossId, status);
	}
}
