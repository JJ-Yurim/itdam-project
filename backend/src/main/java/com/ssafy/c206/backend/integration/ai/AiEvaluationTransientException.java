package com.ssafy.c206.backend.integration.ai;

/** A retryable provider or transport failure. */
public class AiEvaluationTransientException extends RuntimeException {

	public AiEvaluationTransientException(String message) {
		super(message);
	}
}
