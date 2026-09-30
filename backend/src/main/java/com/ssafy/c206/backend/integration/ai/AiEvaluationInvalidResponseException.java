package com.ssafy.c206.backend.integration.ai;

/** The provider returned a response that violates the evaluation contract. */
public class AiEvaluationInvalidResponseException extends RuntimeException {

	public AiEvaluationInvalidResponseException(String message) {
		super(message);
	}
}
