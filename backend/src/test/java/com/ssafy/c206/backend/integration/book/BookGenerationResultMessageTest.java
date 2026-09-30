package com.ssafy.c206.backend.integration.book;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.UUID;

import com.ssafy.c206.backend.common.api.DomainException;
import org.junit.jupiter.api.Test;

class BookGenerationResultMessageTest {
	private static final String CORRELATION_ID = UUID.randomUUID().toString();

	@Test
	void validSuccessMapsEveryContractField() {
		var result = successMessage(BookGenerationMqContract.RESULT_CONTRACT_VERSION,
				List.of(11L), List.of(12L)).toResult();

		assertThat(result.correlationId()).isEqualTo(CORRELATION_ID);
		assertThat(result.usedSubmissionIds()).containsExactly(11L);
		assertThat(result.excludedSubmissionIds()).containsExactly(12L);
		assertThat(result.modelName()).isEqualTo("contract-model");
	}

	@Test
	void rejectsUnknownContractVersionAndInvalidCorrelation() {
		assertThatThrownBy(() -> successMessage("book-generation-mq-v2", List.of(11L), List.of()).toResult())
				.isInstanceOf(DomainException.class);
		var invalidCorrelation = new BookGenerationResultMessage(
				BookGenerationMqContract.RESULT_CONTRACT_VERSION, 1L, "not-a-uuid", false,
				"FAILED", "failed", null, null, null, null, null, null, null);
		assertThatThrownBy(invalidCorrelation::toResult).isInstanceOf(DomainException.class);
	}

	@Test
	void rejectsSubmissionProvenanceThatDoesNotMatchContentBlocks() {
		assertThatThrownBy(() -> successMessage(BookGenerationMqContract.RESULT_CONTRACT_VERSION,
				List.of(99L), List.of()).toResult()).isInstanceOf(DomainException.class);
		assertThatThrownBy(() -> successMessage(BookGenerationMqContract.RESULT_CONTRACT_VERSION,
				List.of(11L), List.of(11L)).toResult()).isInstanceOf(DomainException.class);
	}

	private static BookGenerationResultMessage successMessage(String version, List<Long> used, List<Long> excluded) {
		return new BookGenerationResultMessage(version, 1L, CORRELATION_ID, true, null, null,
				List.of(new BookGenerationResultMessage.ContentBlock(
						1, "USER_ORIGINAL", "원문", 11L)),
				"원문", "요약", used, excluded, "contract-model", "contract-prompt");
	}
}
