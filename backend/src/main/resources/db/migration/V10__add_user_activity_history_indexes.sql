CREATE INDEX idx_boss_submission_user_created_at
    ON boss_submissions (user_id, boss_run_id, created_at DESC);

CREATE INDEX idx_book_contents_author_book
    ON book_contents (author_user_id, book_id);
