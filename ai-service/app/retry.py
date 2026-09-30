"""외부 Provider 호출에 공통으로 쓰는 재시도 헬퍼.

평가 Provider와 임베딩 Provider가 같은 백오프 의미를 유지해야 하므로 여기서 공유한다.
"""

from __future__ import annotations

from datetime import datetime, timezone
from email.utils import parsedate_to_datetime


def retry_after_seconds(value: str | None, now: datetime | None = None) -> float | None:
    """`Retry-After` 헤더를 초 단위로 해석한다. 해석할 수 없으면 None을 반환한다."""
    if value is None:
        return None
    try:
        return max(0.0, float(value))
    except ValueError:
        try:
            retry_at = parsedate_to_datetime(value)
        except (TypeError, ValueError, OverflowError):
            return None
        if retry_at.tzinfo is None:
            retry_at = retry_at.replace(tzinfo=timezone.utc)
        current = now or datetime.now(timezone.utc)
        return max(0.0, (retry_at - current).total_seconds())
