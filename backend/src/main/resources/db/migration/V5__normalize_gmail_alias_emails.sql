-- User.normalizeEmail 규칙 변경(ISSUE-118)의 소급 반영.
-- 새 규칙: gmail.com/googlemail.com의 로컬파트 +tag 제거, googlemail.com 도메인을 gmail.com으로 통일.
-- 로그인·중복 검사 조회 키가 새 규칙으로 만들어지므로, 기존 행을 같은 규칙으로 맞추지 않으면
-- 해당 사용자는 로그인이 불가능해지고 같은 편지함으로 중복 가입이 가능해진다.
-- 정규화 결과가 기존 다른 행과 충돌하면(같은 편지함 계정이 이미 둘) 자동 병합이 불가능하므로
-- 건드리지 않고 남긴다. 남은 행은 로그인 불가 상태이므로 수동 정리 대상이다.

-- +tag 별칭 제거 (googlemail 도메인 통일 포함)
UPDATE users u
SET email = split_part(u.email, '+', 1) || '@gmail.com'
WHERE (u.email LIKE '%+%@gmail.com' OR u.email LIKE '%+%@googlemail.com')
  AND u.email NOT LIKE '+%'
  AND NOT EXISTS (
    SELECT 1 FROM users v
    WHERE v.email = split_part(u.email, '+', 1) || '@gmail.com'
      AND v.id <> u.id
  );

-- +tag 없는 googlemail.com 도메인 통일
UPDATE users u
SET email = split_part(u.email, '@', 1) || '@gmail.com'
WHERE u.email LIKE '%@googlemail.com'
  AND u.email NOT LIKE '%+%@googlemail.com'
  AND NOT EXISTS (
    SELECT 1 FROM users v
    WHERE v.email = split_part(u.email, '@', 1) || '@gmail.com'
      AND v.id <> u.id
  );
