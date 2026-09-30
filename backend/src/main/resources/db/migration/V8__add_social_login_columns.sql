-- 소셜 로그인(ISSUE-120): 계정의 인증 경로와 provider 식별자를 추가한다.
-- 기존 행은 전부 이메일+비밀번호 가입이므로 LOCAL로 채운다.
-- 소셜 계정은 비밀번호가 없으므로 password_hash를 nullable로 완화한다
-- (비밀번호 로그인은 null 해시를 인증 실패로 처리한다).

ALTER TABLE users ALTER COLUMN password_hash DROP NOT NULL;
ALTER TABLE users ADD COLUMN provider VARCHAR(20) NOT NULL DEFAULT 'LOCAL';
ALTER TABLE users ADD COLUMN provider_id VARCHAR(255);

-- 같은 소셜 계정의 재로그인이 항상 같은 사용자로 매칭되게 한다. provider_id가 NULL인
-- LOCAL 행은 PostgreSQL 유니크 제약에서 서로 충돌하지 않는다.
ALTER TABLE users ADD CONSTRAINT uk_users_provider_provider_id UNIQUE (provider, provider_id);
