package com.ssafy.c206.backend.common;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

/**
 * Guards the H2/JPA schema against the committed MVP ERD contract.
 *
 * <p>This intentionally checks database metadata rather than entity annotations so that a
 * mapping change cannot silently weaken a database constraint.</p>
 */
@SpringBootTest
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class SchemaContractTest {

	private static final Set<String> MVP_DOMAIN_TABLES = Set.of(
			"AI_JOBS", "BOOKS", "BOOK_BOOKMARKS", "BOOK_CONTENTS", "BOOK_CONTENT_LIKES", "BOOK_LIKES",
			"BOSSES", "BOSS_MOODS", "BOSS_PHASES",
			"BOSS_PHASE_TEMPLATES", "BOSS_RUNS", "IMAGE_ASSETS",
			"BOSS_SUBMISSION_DAILY_COUNTERS", "BOSS_SUBMISSIONS", "COPYRIGHT_AGREEMENTS",
			"DAILY_WORD_SETS", "DAILY_WORD_SET_ITEMS",
			"MOOD_CATEGORIES", "SENTENCES", "SENTENCE_EVALUATIONS", "SENTENCE_MOODS",
			"SENTENCE_USED_DAILY_WORDS", "SENTENCE_VERSIONS", "USER_DAILY_EVALUATION_USAGE", "USERS",
			"WEEKLY_SENTENCE_SETS", "WEEKLY_SENTENCE_SET_ITEMS", "WORDS");

	@Autowired private JdbcTemplate jdbcTemplate;
	@Autowired private DataSource dataSource;

	@Test
	void createsExactlyTheDomainTablesDeclaredInTheMvpErd() {
		Set<String> actualTables = new LinkedHashSet<>(jdbcTemplate.queryForList("""
				select table_name
				from information_schema.tables
				where table_schema = 'PUBLIC' and table_type = 'BASE TABLE'
				order by table_name
				""", String.class));

		assertThat(actualTables).containsExactlyInAnyOrderElementsOf(MVP_DOMAIN_TABLES);
		assertThat(actualTables).doesNotContain("SENTENCE_EMBEDDINGS", "BOSS_CONSTRAINTS");
	}

	@Test
	void mapsErdColumnTypesNullabilityAndEvaluationSnapshots() {
		assertColumn("MOOD_CATEGORIES", "ID", "SMALLINT", false, null);
		assertColumn("USERS", "STATUS", "CHARACTER VARYING", false, 30);
		assertColumn("USERS", "COPYRIGHT_AGREEMENT_ID", "BIGINT", true, null);
		assertColumn("USERS", "COPYRIGHT_AGREED_AT", "TIMESTAMP WITH TIME ZONE", true, null);
		assertColumn("USERS", "COPYRIGHT_AGREEMENT_EXPIRES_AT", "TIMESTAMP WITH TIME ZONE", true, null);
		assertColumn("COPYRIGHT_AGREEMENTS", "VALID_MONTHS", "INTEGER", false, null);
		assertColumn("DAILY_WORD_SET_ITEMS", "POSITION", "SMALLINT", false, null);
		assertColumn("SENTENCE_VERSIONS", "CONTENT", "CHARACTER VARYING", false, null);
		assertColumn("SENTENCE_VERSIONS", "EVALUATION_STARTED_AT", "TIMESTAMP WITH TIME ZONE", true, null);
		assertColumn("SENTENCE_EVALUATIONS", "WORD_BONUS", "NUMERIC", false, null);
		assertColumn("SENTENCE_EVALUATIONS", "GAME_BASE_ATTACK", "NUMERIC", false, null);
		assertColumn("SENTENCE_MOODS", "RANK", "SMALLINT", false, null);
		assertColumn("BOOKS", "BOSS_RUN_ID", "BIGINT", false, null);
		assertColumn("BOOK_CONTENTS", "SEQUENCE_NO", "INTEGER", false, null);
		assertColumn("IMAGE_ASSETS", "STORAGE_KEY", "CHARACTER VARYING", false, 500);
		assertColumn("BOSS_PHASE_TEMPLATES", "IMAGE_ASSET_ID", "BIGINT", false, null);
		assertColumn("BOSS_PHASES", "IMAGE_ASSET_ID", "BIGINT", true, null);
	}

	@Test
	void enforcesAllMvpIdentityAndIdempotencyUniqueConstraints() throws SQLException {
		assertUnique("USERS", "EMAIL");
		assertUnique("USERS", "NICKNAME");
		assertUnique("COPYRIGHT_AGREEMENTS", "VERSION");
		assertUnique("DAILY_WORD_SETS", "USER_ID", "SERVICE_DATE");
		assertUnique("DAILY_WORD_SET_ITEMS", "DAILY_WORD_SET_ID", "POSITION");
		assertUnique("DAILY_WORD_SET_ITEMS", "DAILY_WORD_SET_ID", "WORD_ID");
		assertUnique("SENTENCE_VERSIONS", "SENTENCE_ID", "VERSION_NO");
		assertUnique("SENTENCE_EVALUATIONS", "SENTENCE_VERSION_ID");
		assertUnique("SENTENCE_USED_DAILY_WORDS", "SENTENCE_VERSION_ID", "DAILY_WORD_SET_ITEM_ID");
		assertUnique("MOOD_CATEGORIES", "CODE");
		assertUnique("SENTENCE_MOODS", "SENTENCE_VERSION_ID", "MOOD_CATEGORY_ID");
		assertUnique("SENTENCE_MOODS", "SENTENCE_VERSION_ID", "RANK");
		assertUnique("USER_DAILY_EVALUATION_USAGE", "USER_ID", "SERVICE_DATE");
		assertUnique("BOSS_MOODS", "BOSS_ID", "MOOD_CATEGORY_ID", "MOOD_ROLE");
		assertUnique("BOSS_PHASES", "BOSS_RUN_ID", "PHASE_NO");
		assertUnique("IMAGE_ASSETS", "STORAGE_KEY");
		assertUnique("BOSS_PHASE_TEMPLATES", "BOSS_ID", "PHASE_NO");
		assertUnique("BOSS_SUBMISSION_DAILY_COUNTERS", "BOSS_RUN_ID", "USER_ID", "SERVICE_DATE");
		assertUnique("BOSS_SUBMISSIONS", "BOSS_RUN_ID", "SENTENCE_VERSION_ID");
		assertUnique("AI_JOBS", "BOSS_RUN_ID", "JOB_TYPE");
		assertUnique("BOOKS", "BOSS_RUN_ID");
		assertUnique("BOOK_CONTENTS", "BOOK_ID", "SEQUENCE_NO");
		assertUnique("WEEKLY_SENTENCE_SETS", "WEEK_START_DATE");
		assertUnique("WEEKLY_SENTENCE_SET_ITEMS", "WEEKLY_SENTENCE_SET_ID", "POSITION");
		assertUnique("WEEKLY_SENTENCE_SET_ITEMS", "WEEKLY_SENTENCE_SET_ID", "BOOK_CONTENT_ID");
		assertUnique("BOOK_CONTENT_LIKES", "USER_ID", "BOOK_CONTENT_ID");
		assertUnique("BOOK_LIKES", "USER_ID", "BOOK_ID");
		assertUnique("BOOK_BOOKMARKS", "USER_ID", "BOOK_ID");

		assertThat(uniqueColumnSets("WORDS"))
				.noneMatch(columns -> columns.equals(List.of("WORD")) || columns.equals(List.of("WORD", "PART_OF_SPEECH")));
	}

	@Test
	void keepsTheErdOwnershipForeignKeysForCoreAggregateBoundaries() throws SQLException {
		assertForeignKey("USERS", "COPYRIGHT_AGREEMENT_ID", "COPYRIGHT_AGREEMENTS");
		assertForeignKey("DAILY_WORD_SETS", "USER_ID", "USERS");
		assertForeignKey("DAILY_WORD_SET_ITEMS", "DAILY_WORD_SET_ID", "DAILY_WORD_SETS");
		assertForeignKey("DAILY_WORD_SET_ITEMS", "WORD_ID", "WORDS");
		assertForeignKey("SENTENCES", "USER_ID", "USERS");
		assertForeignKey("SENTENCE_VERSIONS", "SENTENCE_ID", "SENTENCES");
		assertForeignKey("SENTENCE_EVALUATIONS", "SENTENCE_VERSION_ID", "SENTENCE_VERSIONS");
		assertForeignKey("SENTENCE_USED_DAILY_WORDS", "DAILY_WORD_SET_ITEM_ID", "DAILY_WORD_SET_ITEMS");
		assertForeignKey("SENTENCE_MOODS", "MOOD_CATEGORY_ID", "MOOD_CATEGORIES");
		assertForeignKey("USER_DAILY_EVALUATION_USAGE", "USER_ID", "USERS");
		assertForeignKey("BOSS_MOODS", "BOSS_ID", "BOSSES");
		assertForeignKey("BOSS_RUNS", "BOSS_ID", "BOSSES");
		assertForeignKey("BOSS_PHASES", "BOSS_RUN_ID", "BOSS_RUNS");
		assertForeignKey("IMAGE_ASSETS", "CREATED_BY", "USERS");
		assertForeignKey("BOSS_PHASE_TEMPLATES", "BOSS_ID", "BOSSES");
		assertForeignKey("BOSS_PHASE_TEMPLATES", "IMAGE_ASSET_ID", "IMAGE_ASSETS");
		assertForeignKey("BOSS_PHASES", "IMAGE_ASSET_ID", "IMAGE_ASSETS");
		assertForeignKey("BOSS_SUBMISSIONS", "BOSS_PHASE_ID", "BOSS_PHASES");
		assertForeignKey("BOSS_SUBMISSION_DAILY_COUNTERS", "USER_ID", "USERS");
		assertForeignKey("AI_JOBS", "BOSS_RUN_ID", "BOSS_RUNS");
		assertForeignKey("BOOKS", "BOSS_RUN_ID", "BOSS_RUNS");
		assertForeignKey("BOOK_CONTENTS", "BOOK_ID", "BOOKS");
		assertForeignKey("WEEKLY_SENTENCE_SET_ITEMS", "WEEKLY_SENTENCE_SET_ID", "WEEKLY_SENTENCE_SETS");
		assertForeignKey("WEEKLY_SENTENCE_SET_ITEMS", "BOOK_CONTENT_ID", "BOOK_CONTENTS");
		assertForeignKey("BOOK_CONTENT_LIKES", "USER_ID", "USERS");
		assertForeignKey("BOOK_CONTENT_LIKES", "BOOK_CONTENT_ID", "BOOK_CONTENTS");
		assertForeignKey("BOOK_LIKES", "USER_ID", "USERS");
		assertForeignKey("BOOK_LIKES", "BOOK_ID", "BOOKS");
		assertForeignKey("BOOK_BOOKMARKS", "USER_ID", "USERS");
		assertForeignKey("BOOK_BOOKMARKS", "BOOK_ID", "BOOKS");
	}

	@Test
	void exposesTheErdQueryIndexesUsedByMvpReadAndSubmissionPaths() throws SQLException {
		assertIndex("USERS", "IDX_USERS_COPYRIGHT_AGREEMENT_ID", "COPYRIGHT_AGREEMENT_ID");
		assertIndex("WORDS", "IDX_WORDS_ACTIVE_ID", "IS_ACTIVE", "ID");
		assertIndex("SENTENCE_VERSIONS", "IDX_SENTENCE_VERSION_STATUS", "EVALUATION_STATUS");
		assertIndex("BOSS_RUNS", "IDX_BOSS_RUNS_STATUS_ENDS_AT", "STATUS", "ENDS_AT");
		assertIndex("IMAGE_ASSETS", "IDX_IMAGE_ASSETS_STATUS_CREATED_AT", "STATUS", "CREATED_AT");
		assertIndex("BOSS_SUBMISSIONS", "IDX_BOSS_SUBMISSION_RUN_USER_DATE", "BOSS_RUN_ID", "USER_ID", "SERVICE_DATE");
		assertIndex("BOSS_SUBMISSIONS", "IDX_BOSS_SUBMISSION_PHASE", "BOSS_PHASE_ID");
		assertIndex("AI_JOBS", "IDX_AI_JOBS_STATUS", "STATUS");
	}

	private void assertColumn(String table, String column, String type, boolean nullable, Integer length) {
		Map<String, Object> metadata = jdbcTemplate.queryForMap("""
				select data_type, is_nullable, character_maximum_length
				from information_schema.columns
				where table_schema = 'PUBLIC' and table_name = ? and column_name = ?
				""", table, column);
		assertThat(metadata.get("DATA_TYPE")).isEqualTo(type);
		assertThat(metadata.get("IS_NULLABLE")).isEqualTo(nullable ? "YES" : "NO");
		if (length != null) {
			assertThat(((Number) metadata.get("CHARACTER_MAXIMUM_LENGTH")).intValue()).isEqualTo(length);
		}
	}

	private void assertUnique(String table, String... columns) throws SQLException {
		assertThat(uniqueColumnSets(table)).contains(List.of(columns));
	}

	private void assertIndex(String table, String expectedName, String... columns) throws SQLException {
		Map<String, List<String>> indexes = indexes(table);
		assertThat(indexes).as("indexes for %s", table).containsEntry(expectedName, List.of(columns));
	}

	private void assertForeignKey(String table, String column, String referencedTable) throws SQLException {
		Set<String> foreignKeys = new LinkedHashSet<>();
		try (var connection = dataSource.getConnection(); ResultSet resultSet = connection.getMetaData()
				.getImportedKeys(null, "PUBLIC", table)) {
			while (resultSet.next()) {
				foreignKeys.add(resultSet.getString("FKCOLUMN_NAME") + "->" + resultSet.getString("PKTABLE_NAME"));
			}
		}
		assertThat(foreignKeys).contains(column + "->" + referencedTable);
	}

	private Set<List<String>> uniqueColumnSets(String table) throws SQLException {
		Map<String, List<String>> uniqueIndexes = new TreeMap<>();
		try (var connection = dataSource.getConnection(); ResultSet resultSet = connection.getMetaData()
				.getIndexInfo(null, "PUBLIC", table, true, false)) {
			while (resultSet.next()) {
				String indexName = resultSet.getString("INDEX_NAME");
				String columnName = resultSet.getString("COLUMN_NAME");
				if (indexName != null && columnName != null) {
					uniqueIndexes.computeIfAbsent(indexName, ignored -> new java.util.ArrayList<>()).add(columnName);
				}
			}
		}
		return new LinkedHashSet<>(uniqueIndexes.values());
	}

	private Map<String, List<String>> indexes(String table) throws SQLException {
		Map<String, List<String>> indexes = new TreeMap<>();
		try (var connection = dataSource.getConnection(); ResultSet resultSet = connection.getMetaData()
				.getIndexInfo(null, "PUBLIC", table, false, false)) {
			while (resultSet.next()) {
				String indexName = resultSet.getString("INDEX_NAME");
				String columnName = resultSet.getString("COLUMN_NAME");
				if (indexName != null && columnName != null) {
					indexes.computeIfAbsent(indexName, ignored -> new java.util.ArrayList<>()).add(columnName);
				}
			}
		}
		return indexes;
	}
}
