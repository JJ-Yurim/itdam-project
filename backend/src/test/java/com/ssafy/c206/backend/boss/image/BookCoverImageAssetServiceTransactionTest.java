package com.ssafy.c206.backend.boss.image;

import static org.assertj.core.api.Assertions.assertThat;

import com.ssafy.c206.backend.boss.image.service.BookCoverImageAssetService;
import org.junit.jupiter.api.Test;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

class BookCoverImageAssetServiceTransactionTest {

	@Test
	void requiresAnExistingTransactionForLockedAssetOperations() {
		Transactional annotation = AnnotatedElementUtils.findMergedAnnotation(
				BookCoverImageAssetService.class, Transactional.class);

		assertThat(annotation).isNotNull();
		assertThat(annotation.propagation()).isEqualTo(Propagation.MANDATORY);
	}
}
