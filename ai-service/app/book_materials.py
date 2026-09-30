"""책 생성 재료를 Backend DB에서 직접 읽는다(ISSUE-122).

요청 메시지는 jobId와 correlationId만 나르고(ADR-007), 재료(보스 테마·제출 문장)는
여기서 SELECT 전용 계정으로 조회해 기존 BookGenerationRequest를 조립한다.

[Spring과 공유하는 계약 — 어느 한쪽만 바꾸면 정상 결과가 거부된다]
- 제출 선별 규칙: final_damage 내림차순, id 오름차순, LIMIT 60.
  Spring의 결과 검증(validateSubmissionCoverage)이 같은 규칙으로 기대 집합을
  재계산하므로 순서·상한이 정확히 일치해야 한다.
- 무드 선별 규칙: THEME 우선, weight 내림차순, id 오름차순, 중복 제거 후 3개.

[스키마 결합]
Spring 소유 테이블을 직접 읽으므로 JPA 엔티티·마이그레이션 변경이 이 쿼리를 깨뜨릴
수 있다. 실인프라 E2E(MvpRealInfrastructureE2eTest)가 Flyway가 적용된 실제
PostgreSQL에서 이 경로를 통째로 검증해 결합을 감시한다.
"""

from __future__ import annotations

import asyncpg

from app.config import Settings
from app.schemas.book import (
    BookGenerationRequest,
    BossTheme,
    MAX_SUBMISSIONS,
    SubmittedSentence,
)

_JOB_SQL = """
SELECT job.correlation_id, run.id AS boss_run_id, book.id AS book_id,
       boss.id AS boss_id, boss.name, boss.description
FROM ai_jobs job
JOIN boss_runs run ON run.id = job.boss_run_id
JOIN bosses boss ON boss.id = run.boss_id
LEFT JOIN books book ON book.boss_run_id = run.id
WHERE job.id = $1
"""

_MOODS_SQL = """
SELECT category.code, mood.mood_role
FROM boss_moods mood
JOIN mood_categories category ON category.id = mood.mood_category_id
WHERE mood.boss_id = $1
ORDER BY CASE WHEN mood.mood_role = 'THEME' THEN 0 ELSE 1 END, mood.weight DESC, mood.id
"""

_SUBMISSIONS_SQL = f"""
SELECT submission.id, version.content
FROM boss_submissions submission
JOIN sentence_versions version ON version.id = submission.sentence_version_id
WHERE submission.boss_run_id = $1
ORDER BY submission.final_damage DESC, submission.id ASC
LIMIT {MAX_SUBMISSIONS}
"""


class BookMaterialsError(Exception):
    """재시도해도 결과가 같은 결정적 재료 오류. 실패 envelope로 Spring에 즉시 알린다."""

    def __init__(self, code: str, message: str):
        super().__init__(message)
        self.code = code


async def create_materials_pool(settings: Settings) -> asyncpg.Pool:
    return await asyncpg.create_pool(
        host=settings.book_db_host,
        port=settings.book_db_port,
        database=settings.book_db_name,
        user=settings.book_db_user,
        password=settings.book_db_password,
        min_size=1,
        max_size=settings.book_max_concurrency,
    )


async def load_book_generation_request(
    pool: asyncpg.Pool, job_id: int, correlation_id: str
) -> BookGenerationRequest:
    async with pool.acquire() as connection:
        job = await connection.fetchrow(_JOB_SQL, job_id)
        if job is None:
            raise BookMaterialsError("UNKNOWN_JOB", f"job {job_id} does not exist")
        if job["correlation_id"] != correlation_id:
            # 재사용된 jobId나 잘못 라우팅된 메시지가 엉뚱한 책을 만들지 않게 한다.
            raise BookMaterialsError(
                "CORRELATION_MISMATCH",
                f"job {job_id} correlationId does not match the request",
            )
        if job["book_id"] is None:
            raise BookMaterialsError("UNKNOWN_BOOK", f"job {job_id} has no book row")

        moods = await connection.fetch(_MOODS_SQL, job["boss_id"])
        submissions = await connection.fetch(_SUBMISSIONS_SQL, job["boss_run_id"])

    if not submissions:
        raise BookMaterialsError(
            "NO_SUBMISSIONS", f"boss run {job['boss_run_id']} has no submissions"
        )

    seen: list[str] = []
    for record in moods:
        if record["code"] not in seen:
            seen.append(record["code"])
    return BookGenerationRequest(
        correlationId=correlation_id,
        jobId=job_id,
        bossRunId=job["boss_run_id"],
        bookId=job["book_id"],
        boss=BossTheme(
            name=job["name"],
            description=job["description"],
            moods=seen[:3],
        ),
        submissions=[
            SubmittedSentence(submissionId=record["id"], text=record["content"])
            for record in submissions
        ],
    )
