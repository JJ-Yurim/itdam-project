from datetime import date
from typing import Literal
from uuid import UUID

from pydantic import BaseModel, ConfigDict, Field, field_validator, model_validator


MoodCategory = Literal[
    "JOY",
    "HOPE",
    "LOVE",
    "CALM",
    "NOSTALGIA",
    "SADNESS",
    "LONELINESS",
    "ANGER",
    "TENSION",
    "MYSTERY",
]


class DailyWordCard(BaseModel):
    """문장 평가 시 사용자가 받은 단어의 의미 카드입니다."""

    model_config = ConfigDict(extra="forbid")

    wordId: int = Field(
        gt=0,
        description="DailyWord의 식별자입니다.",
        examples=[9],
    )
    word: str = Field(
        min_length=1,
        description="평가에 사용할 단어입니다.",
        examples=["햇빛"],
    )
    partOfSpeech: str = Field(
        min_length=1,
        description="단어의 품사입니다.",
        examples=["명사"],
    )
    definition: str = Field(
        min_length=1,
        description="단어의 사전 정의입니다.",
        examples=["해에서 나오는 빛"],
    )

    @field_validator("word", "partOfSpeech", "definition")
    @classmethod
    def validate_non_blank(cls, value: str) -> str:
        if not value.strip():
            raise ValueError("value must not be blank")
        return value


class EvaluationRequest(BaseModel):
    """Spring Boot가 AI Service에 보내는 문장 평가 Snapshot입니다."""

    contractVersion: Literal["sentence-evaluation-v1"] = Field(
        default="sentence-evaluation-v1",
        min_length=1,
        description="Spring Boot와 AI Service가 합의한 JSON 계약 버전입니다.",
        examples=["sentence-evaluation-v1"],
    )
    correlationId: UUID = Field(
        description="이번 외부 평가 호출을 추적하기 위한 UUID입니다.",
        examples=["550e8400-e29b-41d4-a716-446655440000"],
    )
    sentenceVersionId: int = Field(
        gt=0,
        description="평가 대상 SentenceVersion의 식별자입니다.",
        examples=[84],
    )
    text: str = Field(
        min_length=1,
        max_length=500,
        description="AI가 평가할 문장 원문입니다. 공백만으로 구성될 수 없습니다.",
        examples=["비가 그친 골목에 햇빛이 천천히 번졌다."],
    )
    serviceDate: date = Field(
        description="DailyWord와 평가 사용량에 적용되는 서비스 날짜입니다.",
        examples=["2026-07-28"],
    )
    dailyWords: list[DailyWordCard] = Field(
        min_length=5,
        max_length=5,
        description="평가 시점에 사용자에게 발급된 단어 카드입니다. 정확히 5개이며 wordId가 중복될 수 없습니다.",
    )

    model_config = ConfigDict(
        extra="forbid",
        json_schema_extra={
            "example": {
                "contractVersion": "sentence-evaluation-v1",
                "correlationId": "550e8400-e29b-41d4-a716-446655440000",
                "sentenceVersionId": 84,
                "text": "비가 그친 골목에 햇빛이 천천히 번졌다.",
                "serviceDate": "2026-07-28",
                "dailyWords": [
                    {
                        "wordId": 9,
                        "word": "햇빛",
                        "partOfSpeech": "명사",
                        "definition": "해에서 나오는 빛",
                    },
                    {
                        "wordId": 10,
                        "word": "골목",
                        "partOfSpeech": "명사",
                        "definition": "큰길에서 갈라져 나온 좁은 길",
                    },
                    {
                        "wordId": 11,
                        "word": "번지다",
                        "partOfSpeech": "동사",
                        "definition": "액체나 기운이 퍼지다",
                    },
                    {
                        "wordId": 12,
                        "word": "고요",
                        "partOfSpeech": "명사",
                        "definition": "조용하고 잠잠한 상태",
                    },
                    {
                        "wordId": 13,
                        "word": "온기",
                        "partOfSpeech": "명사",
                        "definition": "따뜻한 기운",
                    },
                ],
            }
        },
    )

    @field_validator("contractVersion", "text")
    @classmethod
    def validate_non_blank(cls, value: str) -> str:
        if not value.strip():
            raise ValueError("value must not be blank")
        return value

    @model_validator(mode="after")
    def validate_daily_words(self):
        word_ids = [word.wordId for word in self.dailyWords]
        if len(set(word_ids)) != len(word_ids):
            raise ValueError("dailyWords must not contain duplicate wordId values")
        return self


class SafetyResult(BaseModel):
    """문장 안전성 검사 결과입니다."""

    model_config = ConfigDict(extra="forbid")

    status: Literal["PASSED", "REJECTED"] = Field(
        description="안전성 결과입니다. PASSED는 평가 진행, REJECTED는 안전성 거절입니다.",
        examples=["PASSED"],
    )
    reason: str | None = Field(
        default=None,
        description="안전성 거절 또는 판정 사유입니다. 사유가 없으면 null입니다.",
        examples=[None],
    )


class QualityScores(BaseModel):
    """AI가 산출한 다섯 가지 품질 점수입니다."""

    model_config = ConfigDict(extra="forbid")

    completeness: int = Field(
        ge=0, le=100, description="문장이 주제를 충분히 완성했는지에 대한 점수입니다.", examples=[80]
    )
    originality: int = Field(
        ge=0, le=100, description="표현과 관점의 독창성 점수입니다.", examples=[76]
    )
    expression: int = Field(
        ge=0, le=100, description="문장 표현의 명확성과 자연스러움 점수입니다.", examples=[82]
    )
    imagery: int = Field(
        ge=0, le=100, description="장면과 감각을 떠올리게 하는 이미지 점수입니다.", examples=[74]
    )
    emotionalImpact: int = Field(
        ge=0, le=100, description="독자에게 전달되는 정서적 영향 점수입니다.", examples=[79]
    )


class MoodResult(BaseModel):
    """문장에서 추출된 분위기 태그입니다."""

    model_config = ConfigDict(extra="forbid")

    categoryCode: MoodCategory = Field(
        description="사전에 정의된 분위기 분류 코드입니다.",
        examples=["NOSTALGIA"],
    )
    strength: float = Field(
        ge=0.0,
        le=1.0,
        description="해당 분위기가 문장에 나타나는 정도입니다. 0.0~1.0 범위입니다.",
        examples=[0.92],
    )
    rank: int = Field(
        ge=1,
        le=3,
        description="분위기 강도 순위입니다. 1이 가장 높은 순위입니다.",
        examples=[1],
    )


class UsedDailyWordResult(BaseModel):
    """평가 문장에서 실제 사용된 DailyWord 여부입니다."""

    model_config = ConfigDict(extra="forbid")

    wordId: int = Field(
        gt=0,
        description="평가 요청에 포함된 DailyWord의 식별자입니다.",
        examples=[9],
    )
    used: bool = Field(
        description="해당 단어가 평가 문장에 실제로 사용되었는지 여부입니다.",
        examples=[True],
    )


class EvaluationResponse(BaseModel):
    """AI Service가 반환하는 문장 평가 결과입니다."""

    safety: SafetyResult = Field(description="안전성 판정 결과입니다.")
    scores: QualityScores | None = Field(
        default=None,
        description="안전성 통과 시의 다섯 가지 품질 점수입니다. REJECTED이면 null입니다.",
    )
    overallFeedback: str | None = Field(
        default=None,
        description="문장 전체에 대한 AI 피드백입니다. PASSED이면 제공됩니다.",
        examples=["장면과 감정이 자연스럽게 연결되어 있습니다."],
    )
    moods: list[MoodResult] = Field(
        default_factory=list,
        max_length=3,
        description="문장에서 추출한 분위기 태그입니다. 최대 3개이며 REJECTED이면 빈 배열입니다.",
    )
    usedDailyWords: list[UsedDailyWordResult] = Field(
        default_factory=list,
        max_length=5,
        description="요청받은 DailyWord별 실제 사용 여부입니다. REJECTED이면 빈 배열입니다.",
    )
    modelName: str = Field(
        min_length=1,
        description="평가에 사용한 AI 모델 식별자입니다.",
        examples=["fake-evaluator"],
    )
    promptVersion: str = Field(
        min_length=1,
        description="평가에 사용한 Prompt 버전입니다.",
        examples=["prompt-v1"],
    )
    rubricVersion: str = Field(
        min_length=1,
        description="평가 기준표(rubric) 버전입니다.",
        examples=["rubric-v1"],
    )
    embedding: list[float] | None = Field(
        default=None,
        description=(
            "보스 제출 유사도 검사에 사용할 문장 임베딩 벡터입니다. "
            "임베딩 모델이 설정되지 않았거나 생성에 실패했거나 REJECTED이면 null입니다."
        ),
        examples=[None],
    )
    embeddingModel: str | None = Field(
        default=None,
        description=(
            "임베딩 생성에 사용한 모델 식별자입니다. "
            "유사도 비교는 같은 모델로 만든 벡터끼리만 의미가 있으므로 함께 저장합니다."
        ),
        examples=[None],
    )
    embeddingDimension: int | None = Field(
        default=None,
        description="임베딩 벡터의 차원입니다. embedding이 있으면 그 길이와 같습니다.",
        examples=[None],
    )

    model_config = ConfigDict(
        extra="forbid",
        json_schema_extra={
            "examples": [
                {
                    "safety": {"status": "PASSED", "reason": None},
                    "scores": {
                        "completeness": 80,
                        "originality": 76,
                        "expression": 82,
                        "imagery": 74,
                        "emotionalImpact": 79,
                    },
                    "overallFeedback": "장면과 감정이 자연스럽게 연결되어 있습니다.",
                    "moods": [
                        {"categoryCode": "NOSTALGIA", "strength": 0.92, "rank": 1}
                    ],
                    "usedDailyWords": [
                        {"wordId": 9, "used": True},
                        {"wordId": 10, "used": False},
                        {"wordId": 11, "used": False},
                        {"wordId": 12, "used": False},
                        {"wordId": 13, "used": False},
                    ],
                    "modelName": "fake-evaluator",
                    "promptVersion": "prompt-v1",
                    "rubricVersion": "rubric-v1",
                },
                {
                    "safety": {"status": "REJECTED", "reason": "safety policy"},
                    "scores": None,
                    "overallFeedback": None,
                    "moods": [],
                    "usedDailyWords": [],
                    "modelName": "fake-evaluator",
                    "promptVersion": "prompt-v1",
                    "rubricVersion": "rubric-v1",
                },
            ]
        },
    )

    @model_validator(mode="after")
    def validate_result_by_safety(self):
        if self.safety.status == "REJECTED":
            if self.safety.reason is None or not self.safety.reason.strip():
                raise ValueError("REJECTED responses must contain a safety reason")
            if self.scores is not None:
                raise ValueError("REJECTED responses must not contain scores")
            if self.overallFeedback is not None:
                raise ValueError("REJECTED responses must not contain overallFeedback")
            if self.moods or self.usedDailyWords:
                raise ValueError(
                    "REJECTED responses must not contain moods or usedDailyWords"
                )
            return self

        if self.scores is None:
            raise ValueError("PASSED responses must contain scores")
        if self.safety.reason is not None:
            raise ValueError("PASSED responses must not contain a safety reason")
        if self.overallFeedback is None or not self.overallFeedback.strip():
            raise ValueError("PASSED responses must contain overallFeedback")

        categories = [mood.categoryCode for mood in self.moods]
        ranks = [mood.rank for mood in self.moods]
        if len(set(categories)) != len(categories):
            raise ValueError("mood categoryCode values must be unique")
        if len(set(ranks)) != len(ranks):
            raise ValueError("mood rank values must be unique")
        if set(ranks) != set(range(1, len(self.moods) + 1)):
            raise ValueError("mood ranks must be sequential from 1")

        word_ids = [word.wordId for word in self.usedDailyWords]
        if len(set(word_ids)) != len(word_ids):
            raise ValueError("usedDailyWords wordId values must be unique")
        return self

    @model_validator(mode="after")
    def validate_embedding(self):
        # REJECTED 문장은 보스에 제출할 수 없으므로 유사도 비교 대상이 아니다.
        if self.safety.status == "REJECTED" and self.embedding is not None:
            raise ValueError("REJECTED responses must not contain an embedding")

        metadata = (self.embeddingModel, self.embeddingDimension)
        if self.embedding is None:
            if any(value is not None for value in metadata):
                raise ValueError(
                    "embeddingModel and embeddingDimension require an embedding vector"
                )
            return self

        if any(value is None for value in metadata):
            raise ValueError(
                "responses with an embedding must contain embeddingModel and embeddingDimension"
            )
        if not self.embedding:
            raise ValueError("embedding must not be empty")
        if len(self.embedding) != self.embeddingDimension:
            raise ValueError("embedding length must equal embeddingDimension")
        return self


def validate_used_daily_words(request: EvaluationRequest, response: EvaluationResponse) -> None:
    """Validate the request-aware five-entry usage map."""
    if response.safety.status == "REJECTED":
        if response.usedDailyWords:
            raise ValueError("REJECTED responses must contain no usedDailyWords")
        return

    requested_ids = [word.wordId for word in request.dailyWords]
    returned_ids = [word.wordId for word in response.usedDailyWords]
    if returned_ids != requested_ids:
        raise ValueError(
            "PASSED responses must return all five requested word IDs in request order"
        )
