from functools import lru_cache

from pydantic import Field
from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    app_name: str = "mvp-ai-service"
    app_env: str = "local"
    deploy_commit: str = "unknown"
    llm_provider: str | None = None
    llm_api_key: str | None = None
    llm_model: str | None = None
    llm_base_url: str | None = None
    llm_connect_timeout_seconds: float = Field(default=2.0, gt=0)
    llm_read_timeout_seconds: float = Field(default=12.0, gt=0)
    llm_write_timeout_seconds: float = Field(default=5.0, gt=0)
    llm_pool_timeout_seconds: float = Field(default=2.0, gt=0)
    # 시도별 read timeout(12s) 기준으로 재시도 1회가 budget 안에 온전히 들어가고,
    # Backend read timeout(30s)보다 항상 먼저 끝나도록 25s로 유지한다.
    llm_request_budget_seconds: float = Field(default=25.0, gt=0)
    llm_max_concurrency: int = Field(default=5, gt=0)
    llm_max_retries: int = Field(default=1, ge=0, le=3)
    llm_retry_base_delay_seconds: float = Field(default=0.25, ge=0)
    llm_retry_max_delay_seconds: float = Field(default=2.0, ge=0)
    llm_temperature: float = Field(default=0.1, ge=0.0, le=2.0)
    llm_max_output_tokens: int = Field(default=2048, gt=0)
    prompt_version: str = "prompt-v1"
    rubric_version: str = "rubric-v1"

    # 공동 책 생성 전용 설정. 문장 하나를 보는 평가와 달리 제출 문장 수십 개를 한 번에 엮으므로
    # 지연과 출력 길이가 한 자릿수 배 이상 크다. 평가 값을 재사용하면 반드시 잘리거나 예산에서 끊긴다.
    # 최초 기본값은 flash-lite 기준 book-maker 실측(문장 30~60개 기준 15~51초)이었고, 운영 모델을
    # gemini-2.5-pro로 올린 뒤 실측은 문장 30개 기준 61.3초다(ISSUE-210). 60문장 외삽으로도
    # read timeout 180초·예산 400초 안에 들어 타임아웃 상향 없이 유지한다.
    book_llm_model: str | None = None
    # 평가는 재현성을 위해 0.1이지만 책은 창작물이라 온도를 높인다. 다만 0.9는 body/contents
    # 일치 같은 형식 계약을 확률적으로 깨는 경향이 확인돼(책 11, ISSUE-210) 0.7로 내렸다.
    book_llm_temperature: float = Field(default=0.7, ge=0.0, le=2.0)
    # 계약 위반 재시도(ISSUE-207)의 2차 시도 온도. 재시도는 창의성보다 형식 준수가
    # 목적이라 본 시도보다 낮게 두고, 본 시도 온도와 같은 방식으로 운영에서 조정할 수 있다.
    book_llm_retry_temperature: float = Field(default=0.4, ge=0.0, le=2.0)
    # 8192로는 잘린 사례가 있어 여유를 크게 둔다.
    book_max_output_tokens: int = Field(default=32768, gt=0)
    # 책 전용 Client의 기본값이자 provider가 요청 단위로 명시하는 read timeout이다.
    book_read_timeout_seconds: float = Field(default=180.0, gt=0)
    # 시도별 read timeout 180초와 재시도 1회, 백오프가 모두 들어갈 수 있는 총 호출 예산이다.
    # Semaphore 대기 시간은 이 예산에 포함하지 않는다.
    book_request_budget_seconds: float = Field(default=400.0, gt=0)
    # 장시간 호출이 슬롯을 붙잡으므로 평가와 게이트를 공유하면 평가가 굶는다.
    book_max_concurrency: int = Field(default=2, gt=0)
    book_prompt_version: str = "book-prompt-v2"
    # 생성 결과 안전성 재검수(정책 §12.4)에 쓰는 규칙 기반 필터를 끌 수 있는 손잡이.
    safety_filter_enabled: bool = True

    # 애플리케이션 로거(ai_service.*) 레벨. 기본이 WARNING이면 평가·책 생성의 성공
    # 경로가 통째로 묻혀 운영에서 "도는지 안 도는지" 확인할 방법이 없다.
    log_level: str = "INFO"

    # 공동 책 생성 MQ 소비자는 배포 환경에서만 켠다. HTTP 계약 테스트와 단위 테스트는
    # RabbitMQ가 없어도 기존처럼 독립 실행된다.
    book_mq_enabled: bool = False
    # 문장 평가 MQ 소비자(ISSUE-208). 책과 독립된 스위치라 한쪽만 켤 수 있다.
    # 평가 재료는 메시지가 직접 나르므로 책과 달리 DB 계정이 필요 없다.
    evaluation_mq_enabled: bool = False
    rabbitmq_host: str = "localhost"
    rabbitmq_port: int = Field(default=5672, gt=0, le=65535)
    rabbitmq_user: str | None = None
    rabbitmq_password: str | None = None
    rabbitmq_vhost: str = "/"
    # 생성 재료는 요청 메시지가 아니라 Backend DB에서 직접 읽는다(ADR-007, ISSUE-122).
    # 쓰기 없음은 규율이 아니라 SELECT 전용 계정으로 DB가 강제한다.
    book_db_host: str = "localhost"
    book_db_port: int = Field(default=5432, gt=0, le=65535)
    book_db_name: str = "mvp_backend"
    book_db_user: str | None = None
    book_db_password: str | None = None

    # 문장 임베딩은 GMS의 OpenAI 호환 /embeddings 엔드포인트를 호출해 얻는다.
    # 컨테이너 메모리 제한(512MB) 때문에 로컬 임베딩 모델은 올리지 않는다.
    embedding_model: str | None = None
    embedding_base_url: str | None = None
    embedding_api_key: str | None = None
    # vector(N) 컬럼 정의와 반드시 같은 값이어야 한다. 바꾸려면 마이그레이션과 전체 재임베딩이 필요하다.
    embedding_dimensions: int = Field(default=768, gt=0)
    # 평가를 통과한 요청의 임베딩과 다음 문장 평가가 겹칠 수 있다. 별도 게이트로 짧은
    # 임베딩 호출이 무제한 쌓이지 않게 하고, 문장용 연결 풀은 두 게이트의 합으로 잡는다.
    embedding_max_concurrency: int = Field(default=3, gt=0)
    # 임베딩은 평가가 성공한 뒤에 호출되므로, 지연이 길어지면 이미 성공한 평가가 Backend
    # read timeout(30s)에 걸려 통째로 유실된다. 평가 budget(25s)과 합쳐 30s 안에 들도록 짧게 둔다.
    embedding_request_budget_seconds: float = Field(default=3.0, gt=0)

    model_config = SettingsConfigDict(
        env_file=".env",
        env_file_encoding="utf-8",
        extra="ignore",
    )

    @property
    def llm_configured(self) -> bool:
        return all(
            value is not None and value.strip()
            for value in (
                self.llm_provider,
                self.llm_api_key,
                self.llm_model,
                self.llm_base_url,
            )
        )

    @property
    def llm_provider_supported(self) -> bool:
        return self.llm_provider in {"gemini", "gemini-gms"}

    @property
    def book_model(self) -> str | None:
        """책 생성에 쓸 모델. 따로 지정하지 않으면 평가와 같은 모델을 쓴다."""
        if self.book_llm_model is not None and self.book_llm_model.strip():
            return self.book_llm_model
        return self.llm_model

    @property
    def embedding_configured(self) -> bool:
        return all(
            value is not None and value.strip()
            for value in (
                self.embedding_model,
                self.embedding_base_url,
                self.embedding_api_key,
            )
        )

    @property
    def sentence_max_concurrency(self) -> int:
        """동시에 겹칠 수 있는 문장 평가와 후속 임베딩의 최대 외부 HTTP 호출 수."""
        return self.llm_max_concurrency + self.embedding_max_concurrency

    @property
    def book_mq_configured(self) -> bool:
        return not self.book_mq_enabled or all(
            value is not None and value.strip()
            for value in (
                self.rabbitmq_user,
                self.rabbitmq_password,
                self.book_db_user,
                self.book_db_password,
            )
        )

    @property
    def evaluation_mq_configured(self) -> bool:
        return not self.evaluation_mq_enabled or all(
            value is not None and value.strip()
            for value in (
                self.rabbitmq_user,
                self.rabbitmq_password,
            )
        )


@lru_cache
def get_settings() -> Settings:
    return Settings()

