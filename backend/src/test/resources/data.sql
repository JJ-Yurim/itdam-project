INSERT INTO copyright_agreements (
    id, version, title, content, active, effective_at, valid_months, created_at
) VALUES (
    1,
    '1.0',
    '잇담 저작권 이용 동의서',
    '사용자가 작성한 문장은 AI 평가, 공동 책 생성, 도서관 및 명예의 전당 공개를 위해 저장·편집·전송될 수 있습니다. 저작권은 사용자에게 있으며, 서비스는 위 목적 범위에서 비독점적으로 이용할 수 있습니다.',
    TRUE,
    TIMESTAMP WITH TIME ZONE '2026-01-01 00:00:00+09:00',
    12,
    TIMESTAMP WITH TIME ZONE '2026-01-01 00:00:00+09:00'
);
