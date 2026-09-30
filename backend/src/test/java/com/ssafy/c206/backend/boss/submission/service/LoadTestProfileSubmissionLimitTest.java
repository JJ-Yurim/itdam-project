package com.ssafy.c206.backend.boss.submission.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

/**
 * loadtest 프로필이 일일 제출 한도를 실제로 올리는지 확인한다.
 *
 * <p>확인하려는 것은 프로퍼티 파일이 읽혀 정책 빈에 반영되는가 하나뿐이므로, 전체 애플리케이션
 * 컨텍스트 대신 정책 설정만 올린다. 전체 컨텍스트를 쓰면 loadtest 오버레이의 나머지 값
 * (pgvector 유사도 클라이언트, 풀·스레드 크기)이 H2 테스트 컨텍스트에 함께 걸려, 한도와 무관한
 * 설정 변화로 이 테스트가 깨질 수 있다.
 *
 * <p>운영 기본값 3은 {@code BossSubmissionPolicyTest.fourthSequentialSubmissionOnTheSameDayIsRateLimited}가
 * 제출 흐름으로 고정한다. 이 두 테스트가 함께 "기본은 3, 부하 테스트에서만 상향"을 지킨다.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BossSubmissionPolicyConfiguration.class,
		initializers = ConfigDataApplicationContextInitializer.class)
@ActiveProfiles({"test", "loadtest"})
class LoadTestProfileSubmissionLimitTest {

	@Autowired private BossSubmissionPolicy submissionPolicy;

	@Test
	void loadTestProfileAllowsSubmittingBeyondTheProductionDailyLimit() {
		// 기본 한도(3)에서는 0이 되는 지점이다. 여기서 잔여가 남아야 동시 제출 부하를 만들 수 있다.
		assertThat(submissionPolicy.remainingSubmissions(3)).isPositive();
	}
}
