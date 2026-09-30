-- 부하 중 락 경합 스냅샷 한 줄 (ISSUE-198).
--
-- 제출이 boss_runs 행 하나를 SELECT FOR UPDATE로 잠그면, 뒤따르는 백엔드는 Lock 대기로
-- 들어간다. 그 대기 건수와 대기 시간이 컨보이의 직접 증거다.
--
-- 실측에서 결정적인 지표는 lock_waiting이다. 동시 사용자 N에 대해 이 값이 N-1로 유지되면
-- 매 순간 한 세션만 일하고 나머지는 전부 대기한다는 뜻이다.
--
-- idle_in_tx는 이 구조에서 커지지 않는다. 락을 기다리는 백엔드는 state가 'active'이면서
-- wait_event_type이 'Lock'이기 때문이다. 그래도 함께 수집한다. 값이 커진다면 그건 애플리케이션이
-- 트랜잭션을 열어 둔 채 DB 밖 작업을 기다리는 다른 문제라는 신호다.

\pset format unaligned
\pset fieldsep '\t'
\pset tuples_only on

SELECT
    to_char(clock_timestamp(), 'HH24:MI:SS'),
    (SELECT count(*) FROM pg_stat_activity
     WHERE backend_type = 'client backend' AND state = 'active'),
    (SELECT count(*) FROM pg_stat_activity
     WHERE backend_type = 'client backend' AND state = 'idle in transaction'),
    (SELECT count(*) FROM pg_stat_activity
     WHERE backend_type = 'client backend' AND wait_event_type = 'Lock'),
    -- waitstart(PG14+)는 락 대기가 시작된 시각이다. state_change 경과로 추정하면 락 획득 전에
    -- 수행한 작업 시간이 섞여 대기 시간이 과대평가된다.
    (SELECT coalesce(round(max(extract(epoch FROM clock_timestamp() - lock_row.waitstart) * 1000)), 0)
     FROM pg_locks lock_row
     WHERE NOT lock_row.granted AND lock_row.waitstart IS NOT NULL),
    (SELECT count(*) FROM pg_locks lock_row
     JOIN pg_class class_row ON class_row.oid = lock_row.relation
     WHERE class_row.relname = 'boss_runs' AND NOT lock_row.granted);
