package com.ssafy.c206.backend.boss.submission.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class BossSubmissionPolicyValidationTest {

	@Test
	void zeroOrNegativeLimitIsRejectedAtConstruction() {
		// 0 이하를 통과시키면 모든 제출이 RATE_LIMITED로 끝나는데, 예외가 없어 설정 실수가
		// 정책 위반처럼 보인다. 기동 시점에 막아야 원인이 드러난다.
		assertThatThrownBy(() -> new BossSubmissionPolicy(0)).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new BossSubmissionPolicy(-1)).isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void remainingSubmissionsCountsDownToZeroAndStops() {
		BossSubmissionPolicy policy = new BossSubmissionPolicy(3);

		assertThat(policy.remainingSubmissions(0)).isEqualTo(3);
		assertThat(policy.remainingSubmissions(2)).isEqualTo(1);
		assertThat(policy.remainingSubmissions(3)).isZero();
	}

	@Test
	void remainingSubmissionsDoesNotGoNegativeAfterTheLimitIsLowered() {
		// 한도를 내린 뒤에도 이미 제출한 사용자가 음수 잔여 횟수를 받으면 클라이언트 표시가 깨진다.
		assertThat(new BossSubmissionPolicy(1).remainingSubmissions(5)).isZero();
	}
}
