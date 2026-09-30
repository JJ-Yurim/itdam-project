import httpx

from app.config import Settings


def build_sentence_http_client(settings: Settings) -> httpx.AsyncClient:
    """문장 평가와 후속 임베딩이 함께 사용하는 짧은 호출 전용 Client."""
    return httpx.AsyncClient(
        timeout=httpx.Timeout(
            connect=settings.llm_connect_timeout_seconds,
            read=settings.llm_read_timeout_seconds,
            write=settings.llm_write_timeout_seconds,
            pool=settings.llm_pool_timeout_seconds,
        ),
        limits=httpx.Limits(
            max_connections=settings.sentence_max_concurrency,
            max_keepalive_connections=settings.sentence_max_concurrency,
        ),
    )


def build_book_http_client(settings: Settings) -> httpx.AsyncClient:
    """MQ 책 생성처럼 오래 걸리는 호출을 문장 처리 풀에서 격리한 Client."""
    return httpx.AsyncClient(
        timeout=httpx.Timeout(
            connect=settings.llm_connect_timeout_seconds,
            read=settings.book_read_timeout_seconds,
            write=settings.llm_write_timeout_seconds,
            pool=settings.llm_pool_timeout_seconds,
        ),
        limits=httpx.Limits(
            max_connections=settings.book_max_concurrency,
            max_keepalive_connections=settings.book_max_concurrency,
        ),
    )
