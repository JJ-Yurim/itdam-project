package com.ssafy.c206.backend.sentence.evaluation.service;

import java.time.Duration;
import java.util.List;

import com.ssafy.c206.backend.common.time.ServiceClock;
import com.ssafy.c206.backend.sentence.domain.EvaluationStatus;
import com.ssafy.c206.backend.sentence.repository.SentenceVersionRepository;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 접수됐지만 아직 발행되지 못한 평가를 다시 발행하는 릴레이(ISSUE-208).
 *
 * <p>접수 직후 즉시 발행이 실패한 경우(브로커 순단·confirm 타임아웃·발행 직전 재시작)의
 * 보상 경로다. 정상 경로에서는 잡을 것이 없어 인덱스 조회 한 번으로 끝난다.
 *
 * <p>접수 후 유예(기본 10초, confirm 대기 5초보다 길게)가 지난 행만 잡는다. 유예 없이는
 * 아직 confirm을 기다리는 즉시 발행과 경합해 같은 평가를 이중 발행한다(결과 적용은
 * 멱등해 상태는 안 깨지지만 GMS 호출이 두 배로 나간다).
 *
 * <p>발행됐지만 결과가 안 오는 반대편 유실은 기존 stale 회수(EvaluationRecoveryScheduler,
 * 900초)가 실패 처리·사용량 반환으로 수습한다. 릴레이 주기(2초)는 그보다 훨씬 짧아
 * 미발행 행이 stale로 넘어가는 일은 사실상 없다.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "evaluation.dispatch", havingValue = "rabbit")
public class EvaluationDispatchScheduler {

	private final EvaluationDispatchService dispatchService;
	private final SentenceVersionRepository versionRepository;
	private final ServiceClock serviceClock;
	private final int batchSize;
	private final Duration dispatchGrace;

	EvaluationDispatchScheduler(EvaluationDispatchService dispatchService,
			SentenceVersionRepository versionRepository, ServiceClock serviceClock,
			@Value("${evaluation.dispatch-batch-size:100}") int batchSize,
			@Value("${evaluation.dispatch-grace-seconds:10}") long dispatchGraceSeconds) {
		this.dispatchService = dispatchService;
		this.versionRepository = versionRepository;
		this.serviceClock = serviceClock;
		this.batchSize = batchSize;
		this.dispatchGrace = Duration.ofSeconds(dispatchGraceSeconds);
	}

	@Scheduled(fixedDelayString = "${evaluation.dispatch-delay-ms:2000}")
	@SchedulerLock(name = "evaluationDispatchRecovery", lockAtLeastFor = "PT2S", lockAtMostFor = "PT1M")
	public void relayUndispatched() {
		List<Long> versionIds = versionRepository.findUndispatchedEvaluatingIds(
				EvaluationStatus.EVALUATING, serviceClock.now().minus(dispatchGrace),
				PageRequest.of(0, batchSize));
		for (Long versionId : versionIds) {
			try {
				dispatchService.dispatch(versionId);
			} catch (RuntimeException exception) {
				// 한 건의 실패(브로커 거절 등)가 나머지 발행을 막으면 안 된다. 다음 회차가 다시 잡는다.
				log.warn("sentence_evaluation_relay_failed version_id={}", versionId, exception);
			}
		}
	}
}
