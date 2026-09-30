-- 완성된 책의 표시용 전문과 도서관 목록 요약을 저장한다.
-- 본문은 ai-service가 문단까지 구성해 내려주므로 Spring이 블록을 조합하지 않는다.
-- 문단 구성은 이야기 구조를 아는 쪽이 해야 자연스럽고, 기계적 연결로는 만들 수 없다.
ALTER TABLE books ADD COLUMN body TEXT;
ALTER TABLE books ADD COLUMN summary VARCHAR(500);

-- 이미 완성된 책은 요약이 없으므로 채울 수 없다(과거 생성분엔 애초에 없던 값이다).
-- 본문만 book_contents에서 근사 복원한다. 문단 구성은 잃지만 빈 화면보다 낫다.
UPDATE books
SET body = (
    SELECT string_agg(content.content, E'\n\n' ORDER BY content.sequence_no)
    FROM book_contents content
    WHERE content.book_id = books.id
)
WHERE status = 'COMPLETED' AND body IS NULL;
