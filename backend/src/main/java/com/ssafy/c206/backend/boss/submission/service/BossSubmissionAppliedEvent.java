package com.ssafy.c206.backend.boss.submission.service;

import java.math.BigDecimal;

import com.ssafy.c206.backend.boss.domain.BossRunStatus;

/**
 * 제출 반영 트랜잭션 안에서 발행되는 도메인 이벤트.
 *
 * <p>구독자는 {@code @TransactionalEventListener(AFTER_COMMIT)}로 받아야 한다. 커밋 전에 전파하면
 * 롤백된 데미지가 SSE 구독자에게 노출된다.
 *
 * <p>페이즈가 클리어된 제출에서는 클리어된 페이즈의 값(remainingHp 0)과 전환 후 회차 상태를 담는다.
 */
public record BossSubmissionAppliedEvent(long bossRunId, long userId, int phaseNo, long remainingHp,
		long maxHp, long accumulatedDamage, long appliedDamage, BigDecimal baseScore, BossRunStatus runStatus) {

	public BossSubmissionAppliedEvent(long bossRunId, long userId, int phaseNo, long remainingHp,
			long maxHp, long appliedDamage, BigDecimal baseScore, BossRunStatus runStatus) {
		this(bossRunId, userId, phaseNo, remainingHp, maxHp, 0L, appliedDamage, baseScore, runStatus);
	}
}
