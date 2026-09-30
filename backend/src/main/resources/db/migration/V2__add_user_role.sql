-- 관리자 운영 API 보호를 위한 사용자 역할 컬럼 (ISSUE-79)
ALTER TABLE users ADD COLUMN role VARCHAR(20) NOT NULL DEFAULT 'USER';
