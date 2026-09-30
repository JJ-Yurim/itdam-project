package com.ssafy.c206.backend.sentence.evaluation.service;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * 기동 직후 한 번 회수한다.
 *
 * <p>직전 종료로 남은 고착을 첫 사용자가 마주치기 전에 정리하려는 것이다. 이후의 반복
 * 실행은 {@link EvaluationRecoveryScheduler}가 맡는다.
 */
@Component
@Order(20)
@RequiredArgsConstructor
public class EvaluationRecoveryRunner implements ApplicationRunner {

	private final EvaluationRecoveryService recoveryService;

	@Override
	public void run(ApplicationArguments args) {
		recoveryService.recoverStaleEvaluations();
	}
}
