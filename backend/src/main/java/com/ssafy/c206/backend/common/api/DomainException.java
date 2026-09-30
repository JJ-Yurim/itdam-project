package com.ssafy.c206.backend.common.api;

import lombok.Getter;

@Getter
public class DomainException extends RuntimeException {

	private final ErrorCode errorCode;

	public DomainException(ErrorCode errorCode) {
		this(errorCode, errorCode.defaultMessage());
	}

	public DomainException(ErrorCode errorCode, String message) {
		super(message);
		this.errorCode = errorCode;
	}

	public DomainException(ErrorCode errorCode, String message, Throwable cause) {
		super(message, cause);
		this.errorCode = errorCode;
	}

}
