-- 문장 평가 비동기 파이프라인(ISSUE-208)의 발행 상태.
--
-- 새 job 테이블을 만들지 않는다. sentence_versions의 evaluation_status(EVALUATING)와
-- evaluation_started_at이 이미 작업 상태 머신이므로, 발행 여부와 결과 대조용 상관 ID만
-- 얹어 이 테이블 자체를 outbox로 쓴다.
ALTER TABLE sentence_versions
    ADD COLUMN evaluation_dispatched BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN evaluation_correlation_id UUID;

-- 발행 릴레이가 "EVALUATING이고 미발행"만 훑는다. 그 상태는 전체 행의 극히 일부이므로
-- 부분 인덱스로 잡는다.
CREATE INDEX idx_sentence_versions_undispatched_evaluation
    ON sentence_versions (evaluation_started_at)
    WHERE evaluation_status = 'EVALUATING' AND evaluation_dispatched = FALSE;
