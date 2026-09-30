package com.ssafy.c206.backend.sentence.evaluation.service;

import lombok.RequiredArgsConstructor;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 중단된 평가를 주기적으로 회수한다.
 *
 * <p>기동 시 1회만 돌면 운영 중 발생한 고착이 다음 재배포까지 남는다. 회수 대상은
 * stale 판정(evaluation.stale-after-seconds)을 통과한 것뿐이라, 정상 진행 중인 평가를
 * 가로채지 않는다.
 */
@Component
@RequiredArgsConstructor
public class EvaluationRecoveryScheduler {

	private final EvaluationRecoveryService recoveryService;

	@Scheduled(fixedDelayString = "${evaluation.recovery-delay-ms:60000}")
	@SchedulerLock(name = "staleEvaluationRecovery", lockAtLeastFor = "PT1M", lockAtMostFor = "PT10M")
	public void recover() {
		recoveryService.recoverStaleEvaluations();
	}
}
