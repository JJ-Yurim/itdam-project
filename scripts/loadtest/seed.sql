-- 보스 제출 동시성 측정용 시드 (ISSUE-198).
--
-- 제출 API는 사전 조건이 많아 시드 없이는 부하를 만들 수 없다.
--   1. 평가가 완료되고 안전성 검사를 통과한 sentence_version이 있어야 한다.
--   2. 같은 회차에 같은 버전을 두 번 낼 수 없다(uk_boss_submission_run_version).
--      따라서 "사용자 수 x 사용자당 제출 횟수"만큼의 버전이 미리 있어야 한다.
--   3. 임계구역 안의 pgvector 유사도 쿼리를 실제 비용대로 재현하려면 임베딩 행이 있어야 한다.
--      임베딩이 없으면 조인이 비어 쿼리가 즉시 끝나고, 개선 전 락 보유 시간이 과소 측정된다.
--
-- 사용법 (backend가 Flyway 마이그레이션과 MoodCategorySeeder를 이미 실행한 DB에 대해):
--   psql -v users=50 -v versions_per_user=200 -v password=LoadTest1! -f seed.sql
--
-- 재실행 가능하다. 앞선 시드가 남긴 데이터를 먼저 지운 뒤 새로 만든다.

\set ON_ERROR_STOP on

\if :{?users}
\else
\set users 50
\endif
\if :{?versions_per_user}
\else
\set versions_per_user 200
\endif
\if :{?password}
\else
\set password 'LoadTest1!'
\endif

-- 비밀번호 해시를 파일에 박아 두면 Spring의 BCryptPasswordEncoder 설정이 바뀌었을 때
-- 로그인 401의 원인을 찾기 어렵다. 시드 시점에 실제 bcrypt로 만든다.
CREATE EXTENSION IF NOT EXISTS pgcrypto;

BEGIN;

-- 1. 이전 시드 정리 -----------------------------------------------------------
-- 자식 → 부모 순서로 지운다. 부하 테스트 계정과 보스는 이름 규칙으로만 식별하므로
-- 실제 데이터는 건드리지 않는다.

CREATE TEMP TABLE loadtest_users ON COMMIT DROP AS
SELECT id FROM users WHERE email LIKE 'loadtest-%@loadtest.local';

CREATE TEMP TABLE loadtest_runs ON COMMIT DROP AS
SELECT run.id FROM boss_runs run JOIN bosses boss ON boss.id = run.boss_id
WHERE boss.name LIKE 'LoadTest Boss%';

DELETE FROM boss_submissions
WHERE boss_run_id IN (SELECT id FROM loadtest_runs) OR user_id IN (SELECT id FROM loadtest_users);
DELETE FROM boss_submission_daily_counters
WHERE boss_run_id IN (SELECT id FROM loadtest_runs) OR user_id IN (SELECT id FROM loadtest_users);
DELETE FROM boss_phases WHERE boss_run_id IN (SELECT id FROM loadtest_runs);
DELETE FROM boss_runs WHERE id IN (SELECT id FROM loadtest_runs);
DELETE FROM boss_moods WHERE boss_id IN (SELECT id FROM bosses WHERE name LIKE 'LoadTest Boss%');
DELETE FROM bosses WHERE name LIKE 'LoadTest Boss%';

DELETE FROM sentence_embeddings WHERE sentence_version_id IN (
    SELECT version.id FROM sentence_versions version
    JOIN sentences sentence ON sentence.id = version.sentence_id
    WHERE sentence.user_id IN (SELECT id FROM loadtest_users));
DELETE FROM sentence_moods WHERE sentence_version_id IN (
    SELECT version.id FROM sentence_versions version
    JOIN sentences sentence ON sentence.id = version.sentence_id
    WHERE sentence.user_id IN (SELECT id FROM loadtest_users));
DELETE FROM sentence_evaluations WHERE sentence_version_id IN (
    SELECT version.id FROM sentence_versions version
    JOIN sentences sentence ON sentence.id = version.sentence_id
    WHERE sentence.user_id IN (SELECT id FROM loadtest_users));
DELETE FROM sentence_versions WHERE sentence_id IN (
    SELECT id FROM sentences WHERE user_id IN (SELECT id FROM loadtest_users));
DELETE FROM sentences WHERE user_id IN (SELECT id FROM loadtest_users);
DELETE FROM user_daily_evaluation_usage WHERE user_id IN (SELECT id FROM loadtest_users);
DELETE FROM users WHERE id IN (SELECT id FROM loadtest_users);

-- 2. 사용자 -------------------------------------------------------------------
-- 모든 계정이 같은 평문 비밀번호를 쓴다. k6가 계정마다 다른 비밀번호를 들고 다닐 이유가 없고,
-- 이 데이터는 로컬 측정 전용이다.

INSERT INTO users (email, password_hash, nickname, status, role, provider, created_at, updated_at)
SELECT 'loadtest-' || index || '@loadtest.local',
       crypt(:'password', gen_salt('bf', 10)),
       'loadtest-' || index,
       'ACTIVE', 'USER', 'LOCAL', now(), now()
FROM generate_series(1, :users) AS index;

-- 3. 문장과 평가 결과 ---------------------------------------------------------
-- 사용자당 문장 1건에 버전 N개를 단다. sentence_versions는 (sentence_id, version_no)만
-- 유일하면 되므로 문장을 N개 만들 필요가 없다.

-- status는 SentenceStatus(ACTIVE·DELETED)다. sentence_versions의 evaluation_status와 달리
-- DRAFT 값이 없어, 잘못 넣으면 엔티티 매핑 단계에서 500으로만 드러난다.
INSERT INTO sentences (user_id, status, created_at, updated_at)
SELECT id, 'ACTIVE', now(), now() FROM users WHERE email LIKE 'loadtest-%@loadtest.local';

INSERT INTO sentence_versions (sentence_id, version_no, content, evaluation_status, created_at, confirmed_at)
SELECT sentence.id, version_no,
       '부하 테스트 문장 ' || sentence.id || '-' || version_no || ' 입니다.',
       'EVALUATED', now(), now()
FROM sentences sentence
JOIN users u ON u.id = sentence.user_id AND u.email LIKE 'loadtest-%@loadtest.local'
CROSS JOIN generate_series(1, :versions_per_user) AS version_no;

-- hard_min_score 0 · multiplier 1 조합에서 base_score 90은 항상 양수 데미지를 만든다.
INSERT INTO sentence_evaluations (
    sentence_version_id, safety_status, completeness_score, originality_score, expression_score,
    imagery_score, emotional_impact_score, base_score, word_bonus, game_base_attack,
    overall_feedback, model_name, prompt_version, rubric_version, evaluated_at)
SELECT version.id, 'PASSED', 90, 90, 90, 90, 90, 90.00, 0.00, 90.00,
       'loadtest', 'loadtest', 'v1', 'v1', now()
FROM sentence_versions version
JOIN sentences sentence ON sentence.id = version.sentence_id
JOIN users u ON u.id = sentence.user_id AND u.email LIKE 'loadtest-%@loadtest.local';

INSERT INTO sentence_moods (sentence_version_id, mood_category_id, strength, rank)
SELECT version.id, mood.id, mood.strength, mood.rank
FROM sentence_versions version
JOIN sentences sentence ON sentence.id = version.sentence_id
JOIN users u ON u.id = sentence.user_id AND u.email LIKE 'loadtest-%@loadtest.local'
CROSS JOIN (
    SELECT id, 0.800::numeric(4,3) AS strength, 1::smallint AS rank FROM mood_categories WHERE code = 'JOY'
    UNION ALL
    SELECT id, 0.600::numeric(4,3), 2::smallint FROM mood_categories WHERE code = 'MYSTERY'
) AS mood;

-- 4. 임베딩 -------------------------------------------------------------------
-- 무작위 768차원 벡터를 쓴다. 고차원 무작위 벡터쌍의 코사인 유사도는 0 근처라 차단 임계값
-- (0.80)에 걸리지 않으면서, 유사도 쿼리의 실행 비용은 실제 임베딩과 같다.
--
-- 볼륨 함수로 감싸는 이유: 같은 식을 상관 없는 서브쿼리로 두면 PostgreSQL이 InitPlan으로
-- 한 번만 평가해 모든 행이 동일한 벡터를 갖게 된다. 그러면 코사인이 1.0이 되어 두 번째
-- 제출부터 전부 POLICY_REJECTED로 막힌다.
CREATE OR REPLACE FUNCTION loadtest_random_embedding() RETURNS vector AS $$
    SELECT ('[' || string_agg((random() * 2 - 1)::real::text, ',') || ']')::vector
    FROM generate_series(1, 768);
$$ LANGUAGE sql VOLATILE;

INSERT INTO sentence_embeddings (sentence_version_id, embedding, model_name, dimension, created_at)
SELECT version.id, loadtest_random_embedding(), 'loadtest-random', 768, now()
FROM sentence_versions version
JOIN sentences sentence ON sentence.id = version.sentence_id
JOIN users u ON u.id = sentence.user_id AND u.email LIKE 'loadtest-%@loadtest.local';

DROP FUNCTION loadtest_random_embedding();

-- 5. 보스 회차 ----------------------------------------------------------------
-- 측정 도중 페이즈가 클리어되면 페이즈 전환 경로가 섞여 결과를 비교할 수 없다.
-- 한 시나리오가 낼 수 있는 총 데미지보다 훨씬 큰 HP를 단일 페이즈에 준다.

INSERT INTO bosses (name, description, status, created_at, updated_at)
VALUES ('LoadTest Boss', '보스 제출 동시성 측정 전용 보스', 'ACTIVE', now(), now());

INSERT INTO boss_moods (boss_id, mood_category_id, mood_role, weight)
SELECT boss.id, mood.id, mood.role, 1.000
FROM bosses boss
CROSS JOIN (
    SELECT id, 'THEME' AS role FROM mood_categories WHERE code = 'JOY'
    UNION ALL
    SELECT id, 'WEAKNESS' FROM mood_categories WHERE code = 'MYSTERY'
) AS mood
WHERE boss.name = 'LoadTest Boss';

INSERT INTO boss_runs (boss_id, status, current_phase_no, accumulated_damage, started_at, ends_at, created_at, updated_at)
SELECT id, 'ACTIVE', 1, 0, now() - interval '1 hour', now() + interval '30 days', now(), now()
FROM bosses WHERE name = 'LoadTest Boss';

INSERT INTO boss_phases (boss_run_id, phase_no, max_hp, remaining_hp, hard_min_score, recommended_score, damage_multiplier, status)
SELECT run.id, 1, 1000000000, 1000000000, 0.00, NULL, 1.000, 'ACTIVE'
FROM boss_runs run
JOIN bosses boss ON boss.id = run.boss_id AND boss.name = 'LoadTest Boss';

COMMIT;

-- 6. k6가 읽을 요약 -----------------------------------------------------------
\pset format unaligned
\pset tuples_only on

SELECT 'BOSS_RUN_ID=' || run.id FROM boss_runs run
JOIN bosses boss ON boss.id = run.boss_id AND boss.name = 'LoadTest Boss';
SELECT 'USERS=' || count(*) FROM users WHERE email LIKE 'loadtest-%@loadtest.local';
SELECT 'VERSIONS=' || count(*) FROM sentence_versions version
JOIN sentences sentence ON sentence.id = version.sentence_id
JOIN users u ON u.id = sentence.user_id AND u.email LIKE 'loadtest-%@loadtest.local';
