import asyncio
from collections.abc import Callable

import httpx
from fastapi import Request

from app.book_prompt import BookPromptBuilder
from app.book_providers import BookProvider, build_book_provider
from app.config import Settings
from app.embeddings import EmbeddingProvider, build_embedding_provider
from app.prompt import EvaluationPromptBuilder
from app.providers import EvaluationProvider, build_provider
from app.safety import SafetyFilter


ProviderFactory = Callable[
    [Settings, EvaluationPromptBuilder, httpx.AsyncClient, asyncio.Semaphore],
    EvaluationProvider | None,
]

BookProviderFactory = Callable[
    [Settings, BookPromptBuilder, httpx.AsyncClient, asyncio.Semaphore],
    BookProvider | None,
]

EmbeddingProviderFactory = Callable[
    [Settings, httpx.AsyncClient, asyncio.Semaphore],
    EmbeddingProvider | None,
]


def get_app_settings(request: Request) -> Settings:
    """Return settings owned by the current FastAPI application instance."""
    return request.app.state.settings


def get_provider_factory() -> ProviderFactory:
    """Dependency boundary overridden by route tests to prevent real I/O."""
    return build_provider


def get_book_provider_factory() -> BookProviderFactory:
    """Dependency boundary overridden by route tests to prevent real I/O."""
    return build_book_provider


def get_embedding_provider_factory() -> EmbeddingProviderFactory:
    """Dependency boundary overridden by route tests to prevent real I/O."""
    return build_embedding_provider


def get_sentence_http_client(request: Request) -> httpx.AsyncClient:
    return request.app.state.sentence_http_client


def get_book_http_client(request: Request) -> httpx.AsyncClient:
    return request.app.state.book_http_client


def get_provider_concurrency_gate(request: Request) -> asyncio.Semaphore:
    return request.app.state.provider_concurrency_gate


def get_embedding_concurrency_gate(request: Request) -> asyncio.Semaphore:
    return request.app.state.embedding_concurrency_gate


def get_book_concurrency_gate(request: Request) -> asyncio.Semaphore:
    """책 생성 전용 게이트.

    한 건이 최대 수 분 동안 슬롯을 붙잡으므로 평가와 게이트를 공유하면 평가가 굶는다.
    """
    return request.app.state.book_concurrency_gate


def get_safety_filter(request: Request) -> SafetyFilter | None:
    """앱 생성 시점에 선로딩된 필터를 꺼낸다. 사전 문제는 기동 실패로 이미 드러난 뒤다."""
    return request.app.state.safety_filter
