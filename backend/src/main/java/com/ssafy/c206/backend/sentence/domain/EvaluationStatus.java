package com.ssafy.c206.backend.sentence.domain;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(enumAsRef = true)
public enum EvaluationStatus {
	DRAFT,
	EVALUATING,
	EVALUATED,
	REJECTED,
	EVALUATION_FAILED;

	public boolean isUnevaluatedArchiveVisible() {
		return switch (this) {
			case DRAFT, EVALUATING, EVALUATION_FAILED -> true;
			case EVALUATED, REJECTED -> false;
		};
	}
}
