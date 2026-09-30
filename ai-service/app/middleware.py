from uuid import UUID, uuid4

from starlette.middleware.base import BaseHTTPMiddleware, RequestResponseEndpoint
from starlette.requests import Request
from starlette.responses import Response

from app.request_context import reset_request_id, set_request_id


REQUEST_ID_HEADER = "X-Request-ID"


def _parse_or_create(candidate: str | None) -> str:
    if candidate is not None:
        try:
            return str(UUID(candidate))
        except ValueError:
            pass
    return str(uuid4())


class RequestContextMiddleware(BaseHTTPMiddleware):
    async def dispatch(
        self, request: Request, call_next: RequestResponseEndpoint
    ) -> Response:
        request_id = _parse_or_create(request.headers.get(REQUEST_ID_HEADER))
        token = set_request_id(request_id)
        try:
            response = await call_next(request)
            response.headers[REQUEST_ID_HEADER] = request_id
            return response
        finally:
            reset_request_id(token)
