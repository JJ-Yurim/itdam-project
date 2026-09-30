-- 시드가 만든 계정과 문장 버전을 k6가 읽을 JSON으로 내보낸다 (ISSUE-198).
--
-- k6는 DB에 직접 붙을 수 없다. 그렇다고 각 VU가 API로 제출 가능한 문장을 조회하게 하면
-- 측정 대상이 아닌 조회 트래픽이 같은 커넥션 풀과 톰캣 스레드를 나눠 쓰게 되어 제출
-- 응답 시간이 오염된다. 그래서 매핑을 미리 파일로 내보낸다.

\set ON_ERROR_STOP on
\pset format unaligned
\pset tuples_only on

SELECT json_build_object(
    'bossRunId', (
        SELECT run.id FROM boss_runs run
        JOIN bosses boss ON boss.id = run.boss_id
        WHERE boss.name = 'LoadTest Boss' AND run.status = 'ACTIVE'),
    'users', (
        SELECT coalesce(json_agg(json_build_object('email', email, 'versionIds', ids) ORDER BY email), '[]'::json)
        FROM (
            SELECT u.email AS email, json_agg(version.id ORDER BY version.id) AS ids
            FROM users u
            JOIN sentences sentence ON sentence.user_id = u.id
            JOIN sentence_versions version ON version.sentence_id = sentence.id
            WHERE u.email LIKE 'loadtest-%@loadtest.local'
            GROUP BY u.email
        ) AS per_user)
)::text;
