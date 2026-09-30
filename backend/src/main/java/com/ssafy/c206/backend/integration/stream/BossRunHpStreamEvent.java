package com.ssafy.c206.backend.integration.stream;

import java.math.BigDecimal;

/**
 * 보스 회차 HP 변경을 구독자에게 전달하는 전송 DTO.
 *
 * <p>페이즈가 클리어된 제출에서는 클리어된 페이즈의 값(remainingHp 0)과 전환 후 회차 상태를
 * 담는다. 다음 페이즈의 HP는 싣지 않는다 — 클라이언트는 phaseNo 변화를 보고 조회 API로
 * 재동기화한다.
 *
 * <p>runStatus는 전송 안정성을 위해 도메인 enum이 아니라 문자열이다. integration 계층은 도메인에
 * 의존하지 않는다.
 */
public record BossRunHpStreamEvent(long bossRunId, long sequence, int phaseNo, long remainingHp, long maxHp,
		long accumulatedDamage, long appliedDamage, BigDecimal baseScore, String runStatus) {

	public BossRunHpStreamEvent(long bossRunId, long sequence, int phaseNo, long remainingHp, long maxHp,
			long appliedDamage, BigDecimal baseScore, String runStatus) {
		this(bossRunId, sequence, phaseNo, remainingHp, maxHp, 0L, appliedDamage, baseScore, runStatus);
	}
}
