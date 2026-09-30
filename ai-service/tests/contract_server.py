from app.application import create_app
from app.config import Settings
from app.dependencies import get_book_provider_factory, get_provider_factory
from app.schemas.book import (
    BookContentBlock,
    BookGenerationRequest,
    BookGenerationResponse,
)
from app.schemas.evaluation import (
    EvaluationRequest,
    EvaluationResponse,
    MoodResult,
    QualityScores,
    SafetyResult,
    UsedDailyWordResult,
)


class ContractEvaluationProvider:
    """Deterministic provider used only by the cross-service HTTP contract test."""

    async def evaluate(self, request: EvaluationRequest) -> EvaluationResponse:
        if request.text == "contract-rejected":
            return EvaluationResponse(
                safety=SafetyResult(status="REJECTED", reason="contract safety rejection"),
                scores=None,
                overallFeedback=None,
                moods=[],
                usedDailyWords=[],
                modelName="contract-provider",
                promptVersion="contract-prompt-v1",
                rubricVersion="contract-rubric-v1",
            )

        return EvaluationResponse(
            safety=SafetyResult(status="PASSED", reason=None),
            scores=QualityScores(
                completeness=81,
                originality=77,
                expression=83,
                imagery=75,
                emotionalImpact=79,
            ),
            overallFeedback="Spring과 FastAPI 계약이 정상적으로 연결되었습니다.",
            moods=[MoodResult(categoryCode="HOPE", strength=0.88, rank=1)],
            usedDailyWords=[
                UsedDailyWordResult(wordId=word.wordId, used=index == 0)
                for index, word in enumerate(request.dailyWords)
            ],
            modelName="contract-provider",
            promptVersion="contract-prompt-v1",
            rubricVersion="contract-rubric-v1",
            embedding=[1.0] + [0.0] * 767,
            embeddingModel="contract-embedding-v1",
            embeddingDimension=768,
        )


class ContractBookProvider:
    """Deterministic provider used only by the cross-service HTTP contract test.

    제출 문장을 원문 그대로 싣고 그 사이에만 연결 서술을 넣는다. 실제 모델과 달리 원문을
    절대 건드리지 않으므로, Spring이 저장 단계에서 하는 USER_ORIGINAL 일치 검증까지
    네트워크 없이 그대로 확인할 수 있다.
    """

    async def generate(
        self, request: BookGenerationRequest, **_retry_options: object
    ) -> BookGenerationResponse:
        # 재시도 인자(deadline·correction·temperature)는 받기만 하고 쓰지 않는다.
        # 이 provider는 항상 계약을 지키는 결과를 내므로 재시도 경로를 타지 않지만,
        # 호출부가 1차 시도에도 마감을 넘기므로 시그니처는 받아들여야 한다.
        blocks = [
            BookContentBlock(
                sequenceNo=1,
                contentType="AI_GENERATED",
                content="Spring과 FastAPI 계약이 정상적으로 연결되었습니다.",
                sourceSubmissionId=None,
            )
        ]
        for submission in request.submissions:
            blocks.append(
                BookContentBlock(
                    sequenceNo=len(blocks) + 1,
                    contentType="USER_ORIGINAL",
                    content=submission.text,
                    sourceSubmissionId=submission.submissionId,
                )
            )

        # 실제 모델처럼 문단을 구성한 전문을 만든다. 첫 블록(도입) 뒤에 빈 줄을 두어
        # Spring이 조합하지 않고 받은 그대로 저장하는지 종단으로 확인할 수 있게 한다.
        body = "\n\n".join([blocks[0].content, " ".join(block.content for block in blocks[1:])])

        return BookGenerationResponse(
            contents=blocks,
            body=body,
            summary="계약 검증용 요약입니다. 제출된 문장이 하나의 이야기로 엮였습니다.",
            usedSubmissionIds=[
                submission.submissionId for submission in request.submissions
            ],
            excludedSubmissionIds=[],
            modelName="contract-provider",
            promptVersion="contract-book-prompt-v1",
        )


def contract_provider_factory(*_args) -> ContractEvaluationProvider:
    return ContractEvaluationProvider()


def contract_book_provider_factory(*_args) -> ContractBookProvider:
    return ContractBookProvider()


application = create_app(
    Settings(
        _env_file=None,
        app_name="mvp-ai-service-contract-test",
        app_env="contract-test",
    )
)
application.dependency_overrides[get_provider_factory] = (
    lambda: contract_provider_factory
)
application.dependency_overrides[get_book_provider_factory] = (
    lambda: contract_book_provider_factory
)
application.state.book_provider_factory = contract_book_provider_factory
