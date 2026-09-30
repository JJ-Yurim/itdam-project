package com.ssafy.c206.backend.sentence.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;

import org.junit.jupiter.api.Test;

class EvaluationStatusTest {

	@Test
	void classifiesEveryStatusForUnevaluatedArchiveVisibility() {
		var visibleStatuses = Arrays.stream(EvaluationStatus.values())
				.filter(EvaluationStatus::isUnevaluatedArchiveVisible)
				.toList();
		var hiddenStatuses = Arrays.stream(EvaluationStatus.values())
				.filter(status -> !status.isUnevaluatedArchiveVisible())
				.toList();

		assertThat(visibleStatuses).containsExactlyInAnyOrder(
				EvaluationStatus.DRAFT,
				EvaluationStatus.EVALUATING,
				EvaluationStatus.EVALUATION_FAILED);
		assertThat(hiddenStatuses).containsExactlyInAnyOrder(
				EvaluationStatus.EVALUATED,
				EvaluationStatus.REJECTED);
	}
}
