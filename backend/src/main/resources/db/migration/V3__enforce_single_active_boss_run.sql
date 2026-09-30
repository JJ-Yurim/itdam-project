CREATE UNIQUE INDEX uk_boss_runs_one_active_per_boss
    ON boss_runs (boss_id)
    WHERE status = 'ACTIVE';
