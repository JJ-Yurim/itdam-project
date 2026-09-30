package com.ssafy.c206.backend.boss.submission.service;

import java.time.LocalDate;

/**
 * 락 밖 검증·계산 단계가 통과시킨 제출 하나의 상태.
 *
 * <p>엔티티를 담지 않고 식별자와 값 객체만 담는다. 계산 단계와 반영 단계는 서로 다른 트랜잭션이라
 * 엔티티를 넘기면 분리 상태(detached)가 되어 반영 단계에서 지연 로딩이 터질 수 있다.
 *
 * <p>{@code damageCommand}를 함께 들고 다니는 이유는 페이즈 전환 때문이다. 계산 시점의 페이즈가
 * 반영 시점에 이미 넘어가 있으면 새 페이즈 기준으로 다시 계산해야 하는데, 그때 문장·평가·무드를
 * 다시 읽으면 짧게 유지하려던 임계구역이 다시 길어진다.
 */
public record BossSubmissionPreparation(long userId, long bossRunId, long sentenceVersionId, int phaseNo,
		LocalDate serviceDate, DamageCalculationCommand damageCommand, DamageCalculation damage) {
}
