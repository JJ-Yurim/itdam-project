-- ai-service가 책 생성 재료를 직접 읽을 SELECT 전용 계정(ISSUE-122).
-- "쓰기 없음"을 규율이 아니라 DB가 강제한다. 비밀번호는 Flyway 플레이스홀더로
-- 환경변수(AI_READONLY_DB_PASSWORD)에서 주입되며, 비어 있으면 아무것도 하지 않아
-- 계정 없이도 마이그레이션이 통과한다(로컬·테스트 기본값).
DO $$
BEGIN
    IF '${ai_readonly_password}' = '' THEN
        RAISE NOTICE 'mvp_ai_readonly skipped: AI_READONLY_DB_PASSWORD is not set';
        RETURN;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'mvp_ai_readonly') THEN
        EXECUTE format('CREATE ROLE mvp_ai_readonly LOGIN PASSWORD %L', '${ai_readonly_password}');
    ELSE
        EXECUTE format('ALTER ROLE mvp_ai_readonly WITH LOGIN PASSWORD %L', '${ai_readonly_password}');
    END IF;
    EXECUTE format('GRANT CONNECT ON DATABASE %I TO mvp_ai_readonly', current_database());
    GRANT USAGE ON SCHEMA public TO mvp_ai_readonly;
    GRANT SELECT ON ALL TABLES IN SCHEMA public TO mvp_ai_readonly;
    -- 이후 마이그레이션이 만드는 테이블에도 SELECT가 자동 부여되게 한다.
    ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT SELECT ON TABLES TO mvp_ai_readonly;
END $$;
