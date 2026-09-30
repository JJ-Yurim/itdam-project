package com.ssafy.c206.backend.boss.submission.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;

import com.ssafy.c206.backend.common.security.AuthenticatedUser;
import org.junit.jupiter.api.Test;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.transaction.annotation.Transactional;

/**
 * 임계구역 축소가 조용히 무효화되는 회귀를 막는다.
 *
 * <p>{@code BossSubmissionService.submit}은 prepare(잠금 없는 읽기)와 apply(짧은 쓰기)를 서로 다른
 * 트랜잭션으로 실행해야 한다. 이 메서드나 클래스에 {@code @Transactional}이 붙으면 두 경계가
 * 하나의 트랜잭션으로 합쳐져 락 보유 시간이 리팩터링 이전으로 돌아가는데, 동작 자체는 동일해서
 * 어떤 기능 테스트도 실패하지 않는다. 컴파일 타임 구조만이 이 불변식을 드러내므로 여기서 고정한다.
 */
class BossSubmissionTransactionBoundaryTest {

	@Test
	void submitMustNotBeTransactionalSoPrepareAndApplyStaySeparate() throws Exception {
		Method submit = BossSubmissionService.class.getMethod(
				"submit", AuthenticatedUser.class, Long.class, Long.class, boolean.class);

		assertThat(AnnotatedElementUtils.hasAnnotation(submit, Transactional.class))
				.as("submit에 @Transactional이 붙으면 prepare와 apply가 한 트랜잭션으로 합쳐져 "
						+ "임계구역 축소(ISSUE-199)가 무효화된다. 락 보유 시간만 늘고 기능 테스트는 "
						+ "전부 통과하므로 이 테스트가 유일한 방어선이다.")
				.isFalse();
		assertThat(AnnotatedElementUtils.hasAnnotation(BossSubmissionService.class, Transactional.class))
				.as("클래스 수준 @Transactional도 같은 이유로 금지된다.")
				.isFalse();
	}
}
