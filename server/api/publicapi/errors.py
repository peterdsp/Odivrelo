"""The single error shape defined by the v1 contract."""
from __future__ import annotations

from typing import Any

from fastapi import FastAPI, Request
from fastapi.exceptions import RequestValidationError
from fastapi.responses import JSONResponse
from starlette.exceptions import HTTPException as StarletteHTTPException

ERROR_CODES = (
    "not_found",
    "invalid_request",
    "unavailable",
    "release_mismatch",
    "unauthorized",
)

#: The contract pins one HTTP status per error code.
STATUS_FOR_CODE = {
    "invalid_request": 400,
    "unauthorized": 401,
    "not_found": 404,
    "release_mismatch": 409,
    "unavailable": 503,
}


class ApiError(Exception):
    """A contract error. Message text is safe to return to a client."""

    def __init__(self, code: str, message: str, field: str | None = None) -> None:
        if code not in ERROR_CODES:
            raise ValueError(f"unknown error code: {code}")
        super().__init__(message)
        self.code = code
        self.message = message
        self.field = field

    @property
    def status_code(self) -> int:
        return STATUS_FOR_CODE[self.code]

    def payload(self) -> dict[str, Any]:
        return {
            "error": {
                "code": self.code,
                "message": self.message,
                "field": self.field,
            }
        }


def not_found(message: str, field: str | None = None) -> ApiError:
    return ApiError("not_found", message, field)


def invalid_request(message: str, field: str | None = None) -> ApiError:
    return ApiError("invalid_request", message, field)


def unavailable(message: str, field: str | None = None) -> ApiError:
    return ApiError("unavailable", message, field)


def release_mismatch(message: str, field: str | None = None) -> ApiError:
    return ApiError("release_mismatch", message, field)


def unauthorized(message: str = "a valid bearer token is required") -> ApiError:
    return ApiError("unauthorized", message)


def _response(error: ApiError, status_code: int | None = None) -> JSONResponse:
    response = JSONResponse(
        status_code=status_code or error.status_code, content=error.payload()
    )
    if error.code == "unauthorized":
        response.headers["WWW-Authenticate"] = "Bearer"
    response.headers["Cache-Control"] = "no-store"
    return response


def install_error_handlers(app: FastAPI) -> None:
    @app.exception_handler(ApiError)
    async def _api_error(_: Request, error: ApiError) -> JSONResponse:
        return _response(error)

    @app.exception_handler(RequestValidationError)
    async def _validation_error(
        _: Request, error: RequestValidationError
    ) -> JSONResponse:
        first = (error.errors() or [{}])[0]
        location = [str(part) for part in first.get("loc", []) if part != "body"]
        field = location[-1] if location else None
        return _response(
            ApiError(
                "invalid_request",
                str(first.get("msg", "the request could not be validated")),
                field,
            )
        )

    @app.exception_handler(StarletteHTTPException)
    async def _http_error(_: Request, error: StarletteHTTPException) -> JSONResponse:
        code = {
            400: "invalid_request",
            401: "unauthorized",
            403: "unauthorized",
            404: "not_found",
            405: "invalid_request",
            409: "release_mismatch",
            503: "unavailable",
        }.get(error.status_code, "unavailable")
        # The body always carries a contract error code. The HTTP status is
        # preserved, because a 405 with an Allow header is what a client needs
        # to see for a method mismatch, and flattening it to 400 would hide that.
        response = _response(ApiError(code, str(error.detail)), error.status_code)
        for header, value in (getattr(error, "headers", None) or {}).items():
            response.headers[header] = value
        return response
