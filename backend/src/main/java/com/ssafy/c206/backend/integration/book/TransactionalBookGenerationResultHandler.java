package com.ssafy.c206.backend.integration.book;

import com.ssafy.c206.backend.book.service.BookGenerationResultTransaction;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 결과를 상태 전이 트랜잭션으로 반영하는 기본 핸들러.
 *
 * <p>과거에는 local·test 전용 픽스처였지만, MQ 결과 리스너(ISSUE-95)가 모든
 * 환경에서 이 경계를 사용하므로 프로파일 제한을 풀었다. 본문 영속화(ISSUE-96)는
 * BookGenerationResultTransaction 확장으로 이 위에 얹는다.
 */
@Service
@RequiredArgsConstructor
public class TransactionalBookGenerationResultHandler implements BookGenerationResultHandler {

	private final BookGenerationResultTransaction transaction;

	@Override
	public void handle(BookGenerationResult result) {
		transaction.apply(result);
	}
}
