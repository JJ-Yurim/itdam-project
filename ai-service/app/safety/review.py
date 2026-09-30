"""생성 결과 안전성 재검수 (정책 §12.4 "공동 책 생성 완료 전").

제출 원문 그대로인 블록은 검수하지 않는다. 그 문장은 보스에 제출되기 전 평가 단계에서
이미 안전성을 통과했고, 여기서 다시 걸러 봐야 원문을 손댈 방법이 없기 때문이다(원문 보존
우선순위상 AI Service가 임의로 고칠 수 없다). 새로 쓰이거나 편집된 부분만 검사한다.

건너뛸 블록은 **응답의 contentType 라벨이 아니라 제출 원문과 직접 대조해** 이 함수가
스스로 판정한다(ISSUE-212). 라벨을 믿으면 그것이 곧 검수 우회 통로가 된다 — 운영
경로에서는 조립기가 라벨을 원문 대조로 채우지만, 그 보장은 계약이 아니라 현재 구현의
사정일 뿐이라 안전성 판단의 근거로 삼을 수 없다. 같은 이유로 schemas.book의
validate_against_request도 provider가 이미 한 검사를 한 번 더 한다.

body 잔여 검수는 사라졌다. body는 블록을 이어 붙여 서버가 조립하므로 블록으로 설명되지
않는 텍스트가 body에 들어갈 수 없다.
"""

from __future__ import annotations

from dataclasses import dataclass

from app.book_assembly import resolve_provenance
from app.safety.filter import SafetyFilter
from app.schemas.book import BookGenerationRequest, BookGenerationResponse


@dataclass(frozen=True)
class BookSafetyReview:
    """책 본문 전체에 대한 검수 결과."""

    blocked: bool
    # 판정 근거 라벨. 적발된 표현 자체는 담기지 않으므로 그대로 로그에 남겨도 된다.
    labels: tuple[str, ...] = ()
    # 차단된 블록의 순서. 본문 내용이 아니라 위치만 담는다. 0은 요약을 뜻한다.
    blocked_sequences: tuple[int, ...] = ()
    # flag 판정 라벨. 통과시키되 로그에 남겨 필터 임계값을 실측으로 조정할 근거를 모은다.
    flagged_labels: tuple[str, ...] = ()


def review_generated_blocks(
    response: BookGenerationResponse,
    safety_filter: SafetyFilter,
    request: BookGenerationRequest,
) -> BookSafetyReview:
    """AI가 새로 쓰거나 편집한 블록만 검수한다.

    request는 건너뛸 블록을 원문과 대조해 판정하기 위해 받는다. 응답의 라벨은 쓰지 않는다.

    flag는 차단하지 않는다. 창작 문학에서는 어두운 소재나 지명·연락처를 닮은 숫자 표현이
    정상적으로 등장할 수 있고, 이미 비용을 치른 생성 결과를 애매한 근거로 버리지 않기 위해서다.
    하드 차단(block)만 실패로 본다.
    """
    labels: set[str] = set()
    blocked_sequences: list[int] = []
    flagged_labels: set[str] = set()
    originals = {
        submission.submissionId: submission.text for submission in request.submissions
    }

    for block in response.contents:
        original = (
            originals.get(block.sourceSubmissionId)
            if block.sourceSubmissionId is not None
            else None
        )
        # 원문과 한 글자도 다르지 않은 블록만 건너뛴다. 라벨이 무엇이든 상관없다.
        if original is not None and resolve_provenance(block.content, original) == "USER_ORIGINAL":
            continue
        verdict = safety_filter.check_generated_content(block.content)
        if verdict.blocked:
            blocked_sequences.append(block.sequenceNo)
            labels.update(verdict.labels)
        elif verdict.action == "flag":
            flagged_labels.update(verdict.labels)

    # summary는 모델이 새로 쓴 텍스트이고 도서관 목록에 그대로 노출되므로 블록과 같은
    # 기준으로 검수한다.
    summary_verdict = safety_filter.check_generated_content(response.summary)
    if summary_verdict.blocked:
        blocked_sequences.append(0)
        labels.update(summary_verdict.labels)
    elif summary_verdict.action == "flag":
        flagged_labels.update(summary_verdict.labels)

    return BookSafetyReview(
        blocked=bool(blocked_sequences),
        labels=tuple(sorted(labels)),
        blocked_sequences=tuple(blocked_sequences),
        flagged_labels=tuple(sorted(flagged_labels)),
    )
