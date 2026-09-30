from typing import Literal

from pydantic import BaseModel, ConfigDict

from app.schemas.evaluation import EvaluationResponse


class SentenceEvaluationResultEnvelope(BaseModel):
    """AI Service가 Spring 결과 큐로 보내는 sentence-evaluation-mq-v1 envelope.

    성공 결과의 evaluation은 HTTP 경로의 응답 본문(EvaluationResponse)과 같은 형태다.
    안전성 거부는 처리 실패가 아니라 정상 판정이므로 success=true 안의
    safety.status=REJECTED로 표현된다. 실패도 envelope으로 보내야 Spring이 회수
    타임아웃(15분)을 기다리지 않고 즉시 실패 전이·평가권 반환을 할 수 있다.
    """

    model_config = ConfigDict(extra="forbid")

    contractVersion: Literal["sentence-evaluation-mq-v1"] = "sentence-evaluation-mq-v1"
    correlationId: str
    sentenceVersionId: int
    success: bool
    evaluation: EvaluationResponse | None = None
    errorCode: str | None = None
    errorMessage: str | None = None

    @classmethod
    def succeeded(
        cls, sentence_version_id: int, correlation_id: str, response: EvaluationResponse
    ) -> "SentenceEvaluationResultEnvelope":
        return cls(
            correlationId=correlation_id,
            sentenceVersionId=sentence_version_id,
            success=True,
            evaluation=response,
        )

    @classmethod
    def failed(
        cls, sentence_version_id: int, correlation_id: str, error_code: str, message: str
    ) -> "SentenceEvaluationResultEnvelope":
        return cls(
            correlationId=correlation_id,
            sentenceVersionId=sentence_version_id,
            success=False,
            errorCode=error_code,
            errorMessage=message,
        )
