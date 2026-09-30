"""모델 초안(BookDraft)을 서비스 응답(BookGenerationResponse)으로 옮긴다.

ISSUE-212. 이 모듈이 v3 계약의 핵심이다. 모델에게 묻는 것을 줄이고 결정 가능한
것은 서버가 계산해, 검증해야 할 위반 클래스 자체를 없앤다.

모델이 선언하던 것 중 서버 계산으로 옮긴 것:

  - ``body``      모델이 전문을 다시 쓰지 않는다. 블록을 순서대로 이어 붙여 조립한다.
                  문단 판단은 여전히 모델의 몫이지만(paragraphBreakBefore) 문자열
                  재작성은 하지 않으므로, body와 블록이 어긋나는 일이 불가능해진다.
  - ``contentType`` 원문과 같은지 비교해 서버가 판정한다. "편집본을 USER_ORIGINAL로
                  표기"하는 위반이 성립할 수 없다.
  - ``excluded``  사용된 제출의 여집합으로 계산한다(기존 usedSubmissionIds와 같은 방식).
                  커버리지 누락이 불가능해진다.

2026-08-07 실측: 현행 계약은 재시도를 포함해 4시도 4실패했고 위반이 4종이었다.
같은 모델·온도로 이 계약을 쓴 프로토타입은 1시도에 통과했다.
"""

from __future__ import annotations

import logging

from pydantic import (
    BaseModel,
    ConfigDict,
    Field,
    ValidationError,
    field_validator,
    model_validator,
)

from app.schemas.book import (
    MAX_BODY_LENGTH,
    MAX_CONTENT_LENGTH,
    MAX_SUMMARY_LENGTH,
    BookContentBlock,
    BookContractViolationError,
    BookGenerationRequest,
    BookGenerationResponse,
    ContentProvenance,
)


class BookDraftBlockError(BookContractViolationError):
    """초안 블록 구성 규칙 위반(순번 불연속·제출 중복 참조·빈 문장).

    JSON 형식은 멀쩡한데 내용이 규칙을 어긴 경우다. 재시도 교정 문구가 "유효한 단일
    JSON 오브젝트를 출력하라"로 나가면 정작 위반한 규칙을 짚어 주지 못하므로,
    형식 위반과 구분해 전용 지시를 실어 보낸다.
    """


class BookDraftShapeError(BookContractViolationError):
    """초안의 형식 위반(필드 누락·타입 불일치·잉여 필드).

    블록 규칙 위반과 반드시 구분한다. contents가 통째로 빠진 응답에 "sequenceNo는
    1부터 빠짐없이"라는 교정을 보내면 정작 필요한 지시(스키마를 만족하는 단일 JSON
    오브젝트)가 빠진다.
    """


class BookBodyTooLongError(BookContractViolationError):
    """조립된 body가 상한을 넘었다.

    v2까지는 모델이 body를 직접 써서 응답 스키마의 길이 제약이 그 값을 바로 걸렀지만,
    이제 body는 서버가 조립한 파생값이다. 이 오류를 따로 두지 않으면 분량 초과가
    스키마 위반으로 보고되고, 재시도 지시도 무관한 내용이 나가 회복되지 않는다.
    """


# 프롬프트가 요구하는 사용 하한(제출의 3분의 2). 위반해도 실패시키지 않고 경고만 남긴다 —
# 강제하면 이 스토리가 없앤 "억지 끼워맞춤"이 되살아난다. 근거는 build_book_response 주석.
MIN_SUBMISSION_USAGE_RATIO = 2 / 3

logger = logging.getLogger("ai_service.book_generation")


class BookDraftBlock(BaseModel):
    """모델이 돌려주는 본문 블록 하나.

    ``contentType``을 받지 않는다 — 원문과의 비교로 서버가 판정한다.

    잉여 필드를 무시하지 않고 거절한다(``extra="forbid"``). 관용이 더 안전해 보이지만
    여기서는 반대다: ``sourceSubmissionId``를 오타로 쓴 블록을 조용히 통과시키면 그
    블록이 AI_GENERATED로 판정되어 **사용자의 문장이 AI 기여로 기록되고 원작자는 제외
    처리된다.** 오류 없이 기여 표기가 왜곡되는 것이 이 스토리가 없애려는 문제와 같은
    종류다. 거절의 대가는 재시도 1회이며(형식 위반은 schema 지시로 분류된다) 대개
    회복된다.
    """

    model_config = ConfigDict(extra="forbid")

    sequenceNo: int = Field(ge=1, description="본문 내 순서입니다. 1부터 빠짐없이 이어집니다.")
    text: str = Field(
        min_length=1,
        max_length=MAX_CONTENT_LENGTH,
        description="본문에 실제로 실릴 문장입니다. AI 서술이거나, 사용자 문장(그대로 또는 다듬은 것)입니다.",
    )
    sourceSubmissionId: int | None = Field(
        default=None,
        description="사용자 문장을 쓴 블록이면 그 submissionId, AI가 창작한 서술이면 null입니다.",
    )
    paragraphBreakBefore: bool = Field(
        default=False,
        description="이 블록에서 새 문단이 시작되는지 여부입니다. 서버가 조립할 때 앞에 빈 줄을 넣습니다.",
    )

    @field_validator("text")
    @classmethod
    def validate_non_blank(cls, value: str) -> str:
        if not value.strip():
            raise ValueError("value must not be blank")
        return value

    @model_validator(mode="after")
    def validate_source(self):
        if self.sourceSubmissionId is not None and self.sourceSubmissionId <= 0:
            raise ValueError("sourceSubmissionId must be positive")
        return self


class BookDraft(BaseModel):
    """모델의 원출력. 서비스 응답이 아니라 조립 전 초안이다."""

    model_config = ConfigDict(extra="forbid")

    contents: list[BookDraftBlock] = Field(min_length=1)
    summary: str = Field(min_length=1, max_length=MAX_SUMMARY_LENGTH)

    @field_validator("summary")
    @classmethod
    def validate_non_blank(cls, value: str) -> str:
        if not value.strip():
            raise ValueError("value must not be blank")
        return value

    @model_validator(mode="after")
    def validate_contents(self):
        sequence_numbers = [block.sequenceNo for block in self.contents]
        if sequence_numbers != list(range(1, len(self.contents) + 1)):
            raise ValueError("sequenceNo values must be sequential from 1 in list order")
        source_ids = [
            block.sourceSubmissionId
            for block in self.contents
            if block.sourceSubmissionId is not None
        ]
        if len(set(source_ids)) != len(source_ids):
            raise ValueError("a submission must not appear in more than one block")
        return self


# pydantic 오류 타입 중 "형식" 계열. 접미사 _type·_parsing과 함께 판정에 쓴다.
_SHAPE_ERROR_TYPES = frozenset({"missing", "extra_forbidden", "too_short", "too_long"})


def parse_draft(raw: dict) -> BookDraft:
    """초안을 검증하고, 실패를 재시도 지시가 맞는 예외로 바꿔 올린다.

    호출부가 JSON 파싱과 이 검증을 같은 예외로 묶으면 순번 불연속이나 제출 중복 참조가
    "JSON 형식 위반"으로 분류돼 재시도 지시가 어긋난다(BookRetryCorrection 주석 참고).
    같은 이유로 이 함수 안에서도 형식 위반과 블록 규칙 위반을 다시 가른다.
    """
    try:
        return BookDraft.model_validate(raw)
    except ValidationError as exception:
        raise _draft_error(exception) from exception


def _draft_error(exception: ValidationError) -> BookContractViolationError:
    """초안 검증 실패를 위반 성격에 맞는 예외로 분류한다.

    형식 위반이 하나라도 섞여 있으면 그것을 먼저 알린다 — 형식이 어긋난 응답에
    블록 규칙을 설명해 봐야 고쳐지지 않는다.
    """
    types = {error["type"] for error in exception.errors()}
    if any(
        error_type in _SHAPE_ERROR_TYPES or error_type.endswith(("_type", "_parsing"))
        for error_type in types
    ):
        return BookDraftShapeError(f"invalid book draft shape: {exception}")
    if any(error_type.startswith("string_too_long") for error_type in types):
        return BookBodyTooLongError(f"book draft text is too long: {exception}")
    return BookDraftBlockError(f"invalid book draft blocks: {exception}")


def normalize_text(value: str) -> str:
    """블록 문장의 표준 형태. 저장·조립·원문 비교가 모두 이 값을 쓴다.

    한 기준으로 통일해야 저장된 블록과 body가 구조적으로 일치한다. 블록은 문장 단위라
    내부 줄바꿈을 접어도 잃을 정보가 없다.
    """
    return " ".join(value.split())


def assemble_body(blocks: list[BookDraftBlock]) -> str:
    """블록을 문단 구성에 맞춰 이어 붙인다.

    한 문단 안에서는 공백 하나로 잇고, ``paragraphBreakBefore``인 블록에서 새 문단을
    연다. 문단 사이는 빈 줄 하나다. 첫 블록은 플래그와 무관하게 문단을 연다.
    """
    paragraphs: list[list[str]] = []
    for block in blocks:
        if block.paragraphBreakBefore or not paragraphs:
            paragraphs.append([])
        paragraphs[-1].append(normalize_text(block.text))
    return "\n\n".join(" ".join(paragraph) for paragraph in paragraphs)


def resolve_provenance(text: str, original: str) -> ContentProvenance:
    """원문과 같으면 USER_ORIGINAL, 다르면 USER_EDITED.

    라벨을 모델에게 묻지 않으므로 "편집본을 원문으로 표기"하는 위반이 성립하지 않는다.
    비교는 조립·저장과 같은 정규화 기준을 쓴다.
    """
    return "USER_ORIGINAL" if normalize_text(text) == normalize_text(original) else "USER_EDITED"


def build_book_response(
    draft: BookDraft,
    request: BookGenerationRequest,
    model_name: str,
    prompt_version: str,
) -> BookGenerationResponse:
    """초안에 서버 계산분(라벨·body·집계)을 채워 서비스 응답을 만든다.

    Spring으로 나가는 형태는 v1과 같다. 라벨과 body의 출처만 모델에서 서버로 옮겼으므로
    결과 envelope 계약이나 Spring 메시지 스키마를 바꾸지 않는다.
    """
    originals = {
        submission.submissionId: submission.text for submission in request.submissions
    }

    blocks: list[BookContentBlock] = []
    used_submission_ids: list[int] = []
    for block in draft.contents:
        submission_id = block.sourceSubmissionId
        if submission_id is None:
            provenance: ContentProvenance = "AI_GENERATED"
        else:
            original = originals.get(submission_id)
            if original is None:
                # 모델이 지어낸 식별자. 재생성하면 대개 통과하므로 재시도 대상이다.
                raise BookContractViolationError(
                    f"block {block.sequenceNo} references an unknown submission: {submission_id}"
                )
            provenance = resolve_provenance(block.text, original)
            used_submission_ids.append(submission_id)
        blocks.append(
            BookContentBlock(
                sequenceNo=block.sequenceNo,
                contentType=provenance,
                content=normalize_text(block.text),
                sourceSubmissionId=submission_id,
            )
        )

    # 집계는 모델의 자기 보고가 아니라 블록에서 센다. 제외 목록도 여집합으로 계산하므로
    # 커버리지(사용+제외 = 전체)가 구조적으로 보장된다.
    used = set(used_submission_ids)
    excluded_submission_ids = [
        submission.submissionId
        for submission in request.submissions
        if submission.submissionId not in used
    ]

    # 사용 하한은 프롬프트 지침으로만 두고 여기서 실패시키지 않는다. 강제하면 모델이
    # 이야기에 놓을 자리가 없는 문장까지 밀어 넣게 되어, 이 계약 개편이 없앤 "억지
    # 끼워맞춤"이 되살아난다. 대신 미달을 경고로 남겨 실측 근거를 모은다 — 제외가
    # 여집합으로 파생되면서 모델의 의도가 로그 말고는 드러나지 않기 때문이다.
    if request.submissions and len(used) < len(request.submissions) * MIN_SUBMISSION_USAGE_RATIO:
        logger.warning(
            "book_generation_low_usage correlation_id=%s job_id=%s used=%d of %d",
            request.correlationId,
            request.jobId,
            len(used),
            len(request.submissions),
        )

    body = assemble_body(draft.contents)
    if len(body) > MAX_BODY_LENGTH:
        raise BookBodyTooLongError(
            f"assembled body is {len(body)} characters, over the {MAX_BODY_LENGTH} limit"
        )

    return BookGenerationResponse.model_validate(
        {
            "contents": blocks,
            "body": body,
            "summary": draft.summary.strip(),
            "usedSubmissionIds": used_submission_ids,
            "excludedSubmissionIds": excluded_submission_ids,
            "modelName": model_name,
            "promptVersion": prompt_version,
        }
    )
