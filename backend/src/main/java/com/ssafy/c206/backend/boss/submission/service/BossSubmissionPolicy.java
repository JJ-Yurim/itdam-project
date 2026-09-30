package com.ssafy.c206.backend.boss.submission.service;

import lombok.Getter;

/**
 * 회차 진행 상태와 무관한 제출 정책값을 모은다.
 *
 * <p>일일 제출 한도는 운영 기본값이 3이다. 부하 테스트는 이 한도 때문에 한 사용자가 4번째
 * 요청부터 RATE_LIMITED로 끝나 동시 제출을 만들 수 없으므로, 측정 전용 프로필에서만 값을
 * 올릴 수 있도록 상수 대신 주입 가능한 정책으로 둔다.
 */
@Getter
public final class BossSubmissionPolicy {

	private final int dailySubmissionLimit;

	public BossSubmissionPolicy(int dailySubmissionLimit) {
		// 0 이하를 허용하면 모든 제출이 조용히 RATE_LIMITED가 되어 원인을 찾기 어렵다.
		if (dailySubmissionLimit <= 0) {
			throw new IllegalArgumentException(
					"dailySubmissionLimit must be positive but was " + dailySubmissionLimit);
		}
		this.dailySubmissionLimit = dailySubmissionLimit;
	}

	/** 오늘 제출 수를 뺀 잔여 횟수. 한도가 낮아진 뒤에도 음수로 내려가지 않는다. */
	public int remainingSubmissions(int submittedToday) {
		return Math.max(0, dailySubmissionLimit - submittedToday);
	}
}
