from typing import Literal

from pydantic import BaseModel, ConfigDict

from app.schemas.book import BookContentBlock, BookGenerationResponse


class BookGenerationDispatchMessage(BaseModel):
    """Spring이 book.generation.request 큐로 보내는 실행 지시.

    재료는 싣지 않는다(ADR-007). 소비자가 jobId로 DB에서 직접 읽는다.
    """

    model_config = ConfigDict(extra="forbid")

    contractVersion: Literal["book-generation-v1"]
    correlationId: str
    jobId: int


class BookGenerationResultEnvelope(BaseModel):
    """AI Service가 Spring 결과 큐로 보내는 book-generation-mq-v1 envelope."""

    model_config = ConfigDict(extra="forbid")

    contractVersion: Literal["book-generation-mq-v1"] = "book-generation-mq-v1"
    jobId: int
    correlationId: str
    success: bool
    errorCode: str | None = None
    errorMessage: str | None = None
    contents: list[BookContentBlock] | None = None
    body: str | None = None
    summary: str | None = None
    usedSubmissionIds: list[int] | None = None
    excludedSubmissionIds: list[int] | None = None
    modelName: str | None = None
    promptVersion: str | None = None

    @classmethod
    def succeeded(
        cls, job_id: int, correlation_id: str, response: BookGenerationResponse
    ) -> "BookGenerationResultEnvelope":
        return cls(
            jobId=job_id,
            correlationId=correlation_id,
            success=True,
            contents=response.contents,
            body=response.body,
            summary=response.summary,
            usedSubmissionIds=response.usedSubmissionIds,
            excludedSubmissionIds=response.excludedSubmissionIds,
            modelName=response.modelName,
            promptVersion=response.promptVersion,
        )

    @classmethod
    def failed(
        cls, job_id: int, correlation_id: str, error_code: str, message: str
    ) -> "BookGenerationResultEnvelope":
        return cls(
            jobId=job_id,
            correlationId=correlation_id,
            success=False,
            errorCode=error_code,
            errorMessage=message,
        )
