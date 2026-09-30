package com.ssafy.c206.backend.boss.stream.api;

import java.math.BigDecimal;

import com.ssafy.c206.backend.integration.stream.BossRunHpStreamEvent;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * SSE `hp` 이벤트의 data 본문. 시퀀스는 본문이 아니라 SSE 이벤트 id 필드로 나간다.
 *
 * <p>페이즈가 클리어된 제출에서는 클리어된 페이즈의 값(remainingHp 0)이 담긴다. 클라이언트는
 * phaseNo 변화나 ACTIVE가 아닌 runStatus를 보면 조회 API로 재동기화한다.
 */
public record BossRunStreamEventPayload(
		@Schema(description = "보스 회차 식별자입니다.", example = "12") long bossRunId,
		@Schema(description = "이벤트가 반영된 페이즈 번호입니다.", example = "1") int phaseNo,
		@Schema(description = "페이즈의 남은 HP입니다. 페이즈가 클리어된 이벤트에서는 0입니다.", example = "1320") long remainingHp,
		@Schema(description = "페이즈의 최대 HP입니다.", example = "1700") long maxHp,
		@Schema(description = "회차에 누적된 전체 피해의 절대값입니다.", minimum = "0", example = "1280") long accumulatedDamage,
		@Schema(description = "이 이벤트를 만든 제출의 데미지입니다. 구독 시작 스냅숏에서는 0입니다.", example = "110") long appliedDamage,
		@Schema(description = "공격 연출 기준이 되는 AI 기본 점수입니다. 구독 시작 스냅숏에서는 0입니다.", example = "84.20") BigDecimal baseScore,
		@Schema(description = "회차 상태입니다. ACTIVE가 아니면 스트림이 곧 종료됩니다.", example = "ACTIVE") String runStatus) {
	public BossRunStreamEventPayload(long bossRunId, int phaseNo, long remainingHp, long maxHp,
			long appliedDamage, BigDecimal baseScore, String runStatus) {
		this(bossRunId, phaseNo, remainingHp, maxHp, 0L, appliedDamage, baseScore, runStatus);
	}

	public static BossRunStreamEventPayload from(BossRunHpStreamEvent event) {
		return new BossRunStreamEventPayload(event.bossRunId(), event.phaseNo(), event.remainingHp(),
				event.maxHp(), event.accumulatedDamage(), event.appliedDamage(), event.baseScore(), event.runStatus());
	}
}
