"""애플리케이션 로거 설정 회귀 방지.

INFO 로그가 묻히면 배포 후 "책 생성이 실제로 도는지" 확인할 방법이 없어진다.
실제로 MQ 소비자 연결 성공(INFO)이 보이지 않아 경고의 부재로만 판정해야 했던 적이 있다.
"""

import logging

import pytest

from app.application import create_app
from app.config import Settings


@pytest.fixture(autouse=True)
def _restore_service_logger():
    """ai_service 로거는 프로세스 전역이라 원상복구하지 않으면 다른 모듈에 샌다.

    레벨을 WARNING으로 올려둔 채 두면, 자식 로거가 NOTSET인 다른 테스트에서
    실효 레벨이 WARNING이 되어 caplog의 INFO 검증이 실행 순서에 따라 깨진다.
    """
    logger = logging.getLogger("ai_service")
    level, handlers, propagate = logger.level, logger.handlers[:], logger.propagate
    yield
    logger.setLevel(level)
    logger.handlers = handlers
    logger.propagate = propagate


def _settings(**overrides) -> Settings:
    return Settings(_env_file=None, **overrides)


def test_application_logger_emits_info_by_default():
    create_app(_settings())

    service_logger = logging.getLogger("ai_service")

    assert service_logger.level == logging.INFO
    assert service_logger.handlers
    # caplog가 루트 캡처 핸들러로 수집하려면 전파가 살아 있어야 한다.
    assert service_logger.propagate


def test_log_level_is_configurable():
    create_app(_settings(log_level="WARNING"))

    assert logging.getLogger("ai_service").level == logging.WARNING


def test_repeated_app_creation_does_not_stack_handlers():
    create_app(_settings())
    create_app(_settings())

    assert len(logging.getLogger("ai_service").handlers) == 1


def test_unknown_log_level_falls_back_to_info_instead_of_crashing():
    # 로그 레벨 오타로 기동이 실패하면 안 된다. setLevel은 알 수 없는 값에
    # ValueError를 던지므로 폴백이 없으면 create_app 자체가 터진다.
    create_app(_settings(log_level="INFOO"))

    assert logging.getLogger("ai_service").level == logging.INFO


def test_log_level_accepts_lowercase():
    create_app(_settings(log_level="debug"))

    assert logging.getLogger("ai_service").level == logging.DEBUG


def test_externally_attached_handlers_are_preserved():
    # 나중에 파일·APM 핸들러를 붙였을 때 create_app이 조용히 떼어내면 안 된다.
    service_logger = logging.getLogger("ai_service")
    external = logging.NullHandler()
    service_logger.addHandler(external)

    create_app(_settings())

    assert external in service_logger.handlers
