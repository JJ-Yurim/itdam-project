package com.ssafy.c206.backend.common.api;

import org.springframework.http.HttpStatus;

public enum ErrorCode {
	UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "Authentication is required."),
	EMAIL_NOT_VERIFIED(HttpStatus.FORBIDDEN, "Email verification is required."),
	COPYRIGHT_AGREEMENT_MISMATCH(HttpStatus.CONFLICT, "The copyright agreement has changed."),
	COPYRIGHT_AGREEMENT_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE,
			"The current copyright agreement is unavailable."),
	VALIDATION_ERROR(HttpStatus.BAD_REQUEST, "The request is invalid."),
	NOT_FOUND(HttpStatus.NOT_FOUND, "The requested resource was not found."),
	METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "The HTTP method is not allowed for this resource."),
	CONFLICT(HttpStatus.CONFLICT, "The request conflicts with the current state."),
	POLICY_REJECTED(HttpStatus.UNPROCESSABLE_ENTITY, "Safety policy rejected this evaluation."),
	RATE_LIMITED(HttpStatus.TOO_MANY_REQUESTS, "Daily evaluation limit has been reached."),
	FORBIDDEN(HttpStatus.FORBIDDEN, "The requested action is not allowed."),
	INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "An internal error occurred.");

	private final HttpStatus status;
	private final String defaultMessage;

	ErrorCode(HttpStatus status, String defaultMessage) {
		this.status = status;
		this.defaultMessage = defaultMessage;
	}

	public HttpStatus status() {
		return status;
	}

	public String defaultMessage() {
		return defaultMessage;
	}
}
