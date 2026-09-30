"""공동 책 본문 생성 계약.

Spring Boot가 보스런에 제출된 문장을 요청 본문에 담아 보내면, AI Service는 그 문장들을
하나의 이어지는 이야기로 엮어 `book_contents` 행에 그대로 대응하는 블록 배열로 돌려준다.

원문 보존 우선순위(정책 §6.13)를 블록마다 `contentType`으로 표기한다.
  1순위 USER_ORIGINAL — 사용자 원문 그대로.
  2순위 AI_GENERATED  — 도입·연결·마무리를 위해 AI가 새로 쓴 서술.
  3순위 USER_EDITED   — 흐름을 위해 다듬은 사용자 문장.

v3(ISSUE-212)부터 이 라벨은 모델이 선언하는 값이 아니라 **서버가 원문과 비교해
판정한 결과**다(app/book_assembly.py). 여기 정의된 응답 형태는 Spring으로 나가는
계약이라 그대로 유지되지만, 그 값을 채우는 주체가 바뀌었다.
"""

from typing import Literal
from uuid import UUID

from pydantic import BaseModel, ConfigDict, Field, field_validator, model_validator

from app.schemas.evaluation import MoodCategory


class BookContractViolationError(ValueError):
    """모델 응답이 요청 원문과의 계약을 지키지 못했다는 판정.

    provider 어댑터나 검증 코드 자체의 프로그래밍 오류(그 밖의 ValueError)와 구분하기
    위한 전용 타입이다. 라우트는 이 타입만 INVALID_BOOK_RESPONSE(502)로 매핑하고,
    예상 밖 예외는 500으로 새게 두어 원인 파악을 가능하게 한다.
    """


ContentProvenance = Literal["USER_ORIGINAL", "USER_EDITED", "AI_GENERATED"]

# BookContent 엔티티의 content 컬럼 상한과 같은 값이어야 한다. 이보다 길면 Spring이 저장 단계에서
# 거절하므로, 이미 과금이 끝난 결과를 버리지 않도록 AI Service가 먼저 계약 위반으로 잡는다.
MAX_CONTENT_LENGTH = 10_000
# 60문장 입력에서 원문 유실이 두드러졌다(book-maker 실측: 60개 중 35%가 본문에 실리지 못함).
# 그보다 큰 입력은 이야기 완성도와 원문 보존을 동시에 만족시키지 못하므로 계약에서 막는다.
MAX_SUBMISSIONS = 60
# 완성 본문 상한. 상한의 목적은 폭주 방지이지 정상 출력 제약이 아니므로 최악의 정상 입력보다
# 넉넉해야 한다. 제출 60건 × 500자면 사용자 원문만 30,000자이고 body는 USER_ORIGINAL을
# 그대로 담아야 하므로, 이보다 낮게 잡으면 제출이 많은 보스런의 책이 통째로 실패한다.
MAX_BODY_LENGTH = 40_000
# 도서관 목록 미리보기에 실리는 3~4문장 요약. Spring의 books.summary 컬럼 길이와 같아야 한다.
MAX_SUMMARY_LENGTH = 500


class SubmittedSentence(BaseModel):
    """보스런에 제출되어 책 본문 재료가 되는 문장 하나입니다."""

    model_config = ConfigDict(extra="forbid")

    submissionId: int = Field(
        gt=0,
        description="BossSubmission의 식별자입니다. 생성 결과의 sourceSubmissionId로 되돌아옵니다.",
        examples=[101],
    )
    text: str = Field(
        min_length=1,
        max_length=500,
        description="제출된 문장 원문입니다. 공백만으로 구성될 수 없습니다.",
        examples=["안개가 낀 새벽, 나는 발걸음을 옮기며 인적 없는 길을 천천히 걸었다."],
    )

    @field_validator("text")
    @classmethod
    def validate_non_blank(cls, value: str) -> str:
        if not value.strip():
            raise ValueError("value must not be blank")
        return value


class BossTheme(BaseModel):
    """책의 톤을 결정하는 보스 테마입니다."""

    model_config = ConfigDict(extra="forbid")

    name: str = Field(
        min_length=1,
        max_length=150,
        description="보스의 이름입니다.",
        examples=["잊혀진 새벽의 파수꾼"],
    )
    description: str = Field(
        min_length=1,
        description="보스의 설정 문구입니다. 이야기의 배경과 세계관 재료로 씁니다.",
        examples=["아무도 찾지 않는 새벽 골목을 지키며 잊힌 이름을 세는 존재."],
    )
    moods: list[MoodCategory] = Field(
        default_factory=list,
        max_length=3,
        description="책 전체를 관통해야 하는 목표 분위기입니다. 최대 3개이며 중복될 수 없습니다.",
        examples=[["SADNESS", "LONELINESS", "MYSTERY"]],
    )

    @field_validator("name", "description")
    @classmethod
    def validate_non_blank(cls, value: str) -> str:
        if not value.strip():
            raise ValueError("value must not be blank")
        return value

    @model_validator(mode="after")
    def validate_moods(self):
        if len(set(self.moods)) != len(self.moods):
            raise ValueError("boss moods must not contain duplicates")
        return self


class BookGenerationRequest(BaseModel):
    """Spring Boot가 AI Service에 보내는 공동 책 생성 재료입니다."""

    contractVersion: Literal["book-generation-v1"] = Field(
        default="book-generation-v1",
        description="Spring Boot와 AI Service가 합의한 JSON 계약 버전입니다.",
        examples=["book-generation-v1"],
    )
    correlationId: UUID = Field(
        description="이번 책 생성 호출을 추적하기 위한 UUID입니다.",
        examples=["550e8400-e29b-41d4-a716-446655440000"],
    )
    jobId: int = Field(
        gt=0,
        description="BOOK_GENERATION AiJob의 식별자입니다.",
        examples=[12],
    )
    bossRunId: int = Field(
        gt=0,
        description="책의 재료가 된 BossRun의 식별자입니다.",
        examples=[7],
    )
    bookId: int = Field(
        gt=0,
        description="생성 결과가 저장될 Book의 식별자입니다.",
        examples=[3],
    )
    boss: BossTheme = Field(description="이야기의 톤을 결정하는 보스 테마입니다.")
    submissions: list[SubmittedSentence] = Field(
        min_length=1,
        max_length=MAX_SUBMISSIONS,
        description=(
            "본문 재료가 되는 제출 문장입니다. submissionId가 중복될 수 없습니다. "
            "품질과 분위기에 따른 선별은 Spring Boot가 이미 마친 상태로 전달됩니다."
        ),
    )

    model_config = ConfigDict(
        extra="forbid",
        json_schema_extra={
            "example": {
                "contractVersion": "book-generation-v1",
                "correlationId": "550e8400-e29b-41d4-a716-446655440000",
                "jobId": 12,
                "bossRunId": 7,
                "bookId": 3,
                "boss": {
                    "name": "잊혀진 새벽의 파수꾼",
                    "description": "아무도 찾지 않는 새벽 골목을 지키며 잊힌 이름을 세는 존재.",
                    "moods": ["SADNESS", "LONELINESS", "MYSTERY"],
                },
                "submissions": [
                    {
                        "submissionId": 101,
                        "text": "안개가 낀 새벽, 나는 발걸음을 옮기며 인적 없는 길을 천천히 걸었다.",
                    },
                    {
                        "submissionId": 102,
                        "text": "창틀에 앉은 먼지를 손가락으로 쓸어내리며 나는 한숨을 쉬었다.",
                    },
                ],
            }
        },
    )

    @model_validator(mode="after")
    def validate_submissions(self):
        submission_ids = [submission.submissionId for submission in self.submissions]
        if len(set(submission_ids)) != len(submission_ids):
            raise ValueError("submissions must not contain duplicate submissionId values")
        return self


class BookContentBlock(BaseModel):
    """book_contents 한 행에 그대로 대응하는 본문 블록입니다."""

    model_config = ConfigDict(extra="forbid")

    sequenceNo: int = Field(
        ge=1,
        description="본문 내 순서입니다. 1부터 빠짐없이 이어져야 합니다.",
        examples=[1],
    )
    contentType: ContentProvenance = Field(
        description=(
            "블록의 출처입니다. USER_ORIGINAL은 사용자 원문 그대로, "
            "USER_EDITED는 핵심 의미를 유지한 최소 편집, AI_GENERATED는 AI가 새로 쓴 서술입니다."
        ),
        examples=["USER_ORIGINAL"],
    )
    content: str = Field(
        min_length=1,
        max_length=MAX_CONTENT_LENGTH,
        description="블록 본문입니다. 공백만으로 구성될 수 없습니다.",
        examples=["안개가 낀 새벽, 나는 발걸음을 옮기며 인적 없는 길을 천천히 걸었다."],
    )
    sourceSubmissionId: int | None = Field(
        default=None,
        description=(
            "이 블록의 원본 제출 식별자입니다. "
            "USER_ORIGINAL과 USER_EDITED는 반드시 있어야 하고 AI_GENERATED는 null입니다."
        ),
        examples=[101],
    )

    @field_validator("content")
    @classmethod
    def validate_non_blank(cls, value: str) -> str:
        if not value.strip():
            raise ValueError("value must not be blank")
        return value

    @model_validator(mode="after")
    def validate_provenance(self):
        if self.contentType == "AI_GENERATED":
            if self.sourceSubmissionId is not None:
                raise ValueError("AI_GENERATED blocks must not reference a submission")
            return self
        if self.sourceSubmissionId is None:
            raise ValueError(f"{self.contentType} blocks must reference a submission")
        if self.sourceSubmissionId <= 0:
            raise ValueError("sourceSubmissionId must be positive")
        return self


class BookGenerationResponse(BaseModel):
    """AI Service가 반환하는 공동 책 본문입니다."""

    contents: list[BookContentBlock] = Field(
        min_length=1,
        description=(
            "순서대로 이어지는 본문 블록입니다. 기여 추적(정책 §11.5)의 근거이며, "
            "독자에게 보여줄 최종 본문은 body를 사용합니다."
        ),
    )
    body: str = Field(
        min_length=1,
        max_length=MAX_BODY_LENGTH,
        description=(
            "문단까지 완성된 표시용 전문입니다. 문단은 빈 줄로 구분합니다. "
            "블록을 기계적으로 이어 붙이면 문단 구성이 사라지므로 모델이 직접 구성합니다. "
            "USER_ORIGINAL 문장은 이 안에도 한 글자도 바뀌지 않은 채 들어 있어야 합니다."
        ),
        examples=["그 겨울, 도시는 오래 앓은 사람처럼 낮게 숨을 쉬었다.\n\n안개가 낀 새벽, 나는 발걸음을 옮기며 인적 없는 길을 천천히 걸었다."],
    )
    summary: str = Field(
        min_length=1,
        max_length=MAX_SUMMARY_LENGTH,
        description="도서관 목록에 노출할 3~4문장 요약입니다. 본문에 없는 사실을 지어내지 않습니다.",
        examples=["잊힌 이름을 세는 파수꾼의 새벽에서 이야기가 시작된다. 인물들은 각자의 골목을 걸으며 잃어버린 것을 떠올린다. 흩어진 기억이 하나의 목소리로 모이며 겨울이 지나간다."],
    )
    usedSubmissionIds: list[int] = Field(
        description=(
            "본문에 실제로 실린 제출 식별자입니다. 블록에서 계산한 값이며 모델의 자기 보고가 아닙니다."
        ),
        examples=[[101, 102]],
    )
    excludedSubmissionIds: list[int] = Field(
        description=(
            "핵심 의미를 바꾸지 않고는 이야기에 넣을 수 없어 본문에서 제외한 제출 식별자입니다."
        ),
        examples=[[]],
    )
    modelName: str = Field(
        min_length=1,
        description="본문 생성에 사용한 AI 모델 식별자입니다.",
        examples=["gemini-2.5-pro"],
    )
    promptVersion: str = Field(
        min_length=1,
        description="본문 생성에 사용한 Prompt 버전입니다.",
        examples=["book-prompt-v2"],
    )

    model_config = ConfigDict(
        extra="forbid",
        json_schema_extra={
            "example": {
                "contents": [
                    {
                        "sequenceNo": 1,
                        "contentType": "AI_GENERATED",
                        "content": "그 겨울, 도시는 오래 앓은 사람처럼 낮게 숨을 쉬었다.",
                        "sourceSubmissionId": None,
                    },
                    {
                        "sequenceNo": 2,
                        "contentType": "USER_ORIGINAL",
                        "content": "안개가 낀 새벽, 나는 발걸음을 옮기며 인적 없는 길을 천천히 걸었다.",
                        "sourceSubmissionId": 101,
                    },
                ],
                "body": "그 겨울, 도시는 오래 앓은 사람처럼 낮게 숨을 쉬었다.\n\n안개가 낀 새벽, 나는 발걸음을 옮기며 인적 없는 길을 천천히 걸었다.",
                "summary": "겨울 도시의 새벽에서 이야기가 시작된다. 인물은 인적 없는 길을 걸으며 잊힌 것을 떠올린다.",
                "usedSubmissionIds": [101],
                "excludedSubmissionIds": [102],
                "modelName": "gemini-2.5-pro",
                "promptVersion": "book-prompt-v2",
            }
        },
    )

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

        if self.usedSubmissionIds != source_ids:
            raise ValueError("usedSubmissionIds must list the block sources in body order")
        if set(self.usedSubmissionIds) & set(self.excludedSubmissionIds):
            raise ValueError("a submission must not be both used and excluded")
        if len(set(self.excludedSubmissionIds)) != len(self.excludedSubmissionIds):
            raise ValueError("excludedSubmissionIds must not contain duplicates")
        return self


def validate_against_request(
    request: BookGenerationRequest, response: BookGenerationResponse
) -> None:
    """요청 재료를 알아야만 할 수 있는 검증.

    v3(ISSUE-212)에서 크게 줄었다. 라벨·body·집계를 서버가 계산하므로 원문 보존
    판정과 body 일치는 구조적으로 위반이 불가능해졌고, 남은 것은 모델이 직접 고르는
    유일한 값인 **제출 식별자**뿐이다.

    provider 어댑터가 이미 같은 검사를 하지만 여기서 한 번 더 본다. 응답을 만드는
    경로가 provider 하나뿐이라는 보장은 계약이 아니라 현재 구현의 사정일 뿐이다.
    """
    known_ids = {submission.submissionId for submission in request.submissions}

    for block in response.contents:
        submission_id = block.sourceSubmissionId
        if submission_id is not None and submission_id not in known_ids:
            raise BookContractViolationError(
                f"block {block.sequenceNo} references an unknown submission: {submission_id}"
            )

    covered = set(response.usedSubmissionIds) | set(response.excludedSubmissionIds)
    if covered != known_ids:
        raise BookContractViolationError(
            "every submission must be either used in the body or listed as excluded"
        )
