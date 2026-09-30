from pydantic import BaseModel, ConfigDict, Field


class ErrorResponse(BaseModel):
    """AI Service 내부 API의 오류 응답입니다."""

    errorCode: str = Field(
        min_length=1,
        description="프로그램이 처리한 오류의 식별 코드입니다.",
        examples=["LLM_NOT_CONFIGURED"],
    )
    message: str = Field(
        min_length=1,
        description="오류 원인과 현재 처리 상태를 설명하는 메시지입니다.",
        examples=["LLM provider, API key, and model are not configured"],
    )

    model_config = ConfigDict(
        extra="forbid",
        json_schema_extra={
            "example": {
                "errorCode": "LLM_NOT_CONFIGURED",
                "message": "LLM provider, API key, and model are not configured",
            }
        },
    )
