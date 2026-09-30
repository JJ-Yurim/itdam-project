-- 책 공개 여부를 생성 상태와 분리한다(ISSUE-175).
-- DEFAULT 'HIDDEN'은 마이그레이션 적용 후 구버전 인스턴스가 남아 있는 배포 구간에서
-- visibility를 채우지 않는 INSERT가 NOT NULL 위반으로 실패하지 않게 한다.
ALTER TABLE books ADD COLUMN visibility VARCHAR(20) DEFAULT 'HIDDEN';
-- 기존 완료 책은 지금까지처럼 공개로, 생성 중·실패 책은 숨김으로 백필한다.
UPDATE books SET visibility = CASE WHEN status = 'COMPLETED' THEN 'PUBLIC' ELSE 'HIDDEN' END;
ALTER TABLE books ALTER COLUMN visibility SET NOT NULL;

-- 도서관 목록·기여 책·주간 피드 후보가 모두 (status, visibility) 조합으로 거른다.
CREATE INDEX idx_books_status_visibility ON books (status, visibility);
