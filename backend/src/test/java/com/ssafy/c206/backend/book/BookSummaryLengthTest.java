package com.ssafy.c206.backend.book;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;

import com.ssafy.c206.backend.book.domain.Book;
import com.ssafy.c206.backend.boss.domain.BossRun;
import com.ssafy.c206.backend.common.api.DomainException;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

/**
 * 요약 길이 상한을 코드포인트 기준으로 고정하는 도메인 테스트.
 *
 * <p>영속 계층을 거치지 않는다. 테스트용 H2는 VARCHAR 길이를 UTF-16 단위로 세지만 운영
 * PostgreSQL은 문자 수로 세므로, 저장까지 태우면 도메인 규칙이 아니라 H2의 제약을 재는
 * 테스트가 된다.
 */
class BookSummaryLengthTest {

	private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

	@Test
	void acceptsSummaryAtTheLimitEvenWhenItContainsSurrogatePairs() {
		// ai-service(문자 수)와 PostgreSQL VARCHAR(500)(문자 수)이 통과시키는 값이다.
		// UTF-16 단위로 셌다면 600으로 계산돼 이미 과금이 끝난 결과를 여기서만 버리게 된다.
		String summary = "📖".repeat(300);

		assertThatCode(() -> generatingBook().complete(NOW, "본문입니다.", summary)).doesNotThrowAnyException();
	}

	@Test
	void rejectsSummaryLongerThanTheColumnInCodePoints() {
		String summary = "가".repeat(501);

		assertThatThrownBy(() -> generatingBook().complete(NOW, "본문입니다.", summary))
				.isInstanceOf(DomainException.class);
	}

	private static Book generatingBook() {
		return Book.generating(Mockito.mock(BossRun.class), NOW);
	}
}
