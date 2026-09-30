package com.ssafy.c206.backend.sentence.evaluation.service;

import java.time.Duration;
import java.time.Instant;

import com.ssafy.c206.backend.common.api.DomainException;
import com.ssafy.c206.backend.common.api.ErrorCode;
import com.ssafy.c206.backend.common.time.ServiceClock;
import com.ssafy.c206.backend.sentence.domain.EvaluationStatus;
import com.ssafy.c206.backend.sentence.repository.SentenceVersionRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

/**
 * 끝나지 못한 평가를 회수한다.
 *
 * <p>평가는 동기 API라 요청이 끊기면 아무도 마무리하지 않는다. 백엔드 재시작이나 사용자
 * 이탈로 EVALUATING에 남으면 화면은 "평가가 진행 중"을 계속 보여주고 평가 버튼도 잠겨,
 * 그 문장은 다시 손댈 수 없다. 기동 시 한 번만 회수하면 재시작으로 생긴 고착이 다음
 * 재배포까지 남으므로 주기적으로도 돈다.
 *
 * <p>실제 상태 전이와 사용량 반환은 {@link EvaluationRecoveryTransaction}이 건별 트랜잭션으로
 * 처리한다. 여기서는 대상만 고르고 실패를 격리한다.
 */
@Slf4j
@Service
public class EvaluationRecoveryService {

	private final SentenceVersionRepository versionRepository;
	private final EvaluationRecoveryTransaction recoveryTransaction;
	private final ServiceClock serviceClock;
	private final Duration staleEvaluationTimeout;
	private final int batchSize;

	public EvaluationRecoveryService(SentenceVersionRepository versionRepository,
			EvaluationRecoveryTransaction recoveryTransaction, ServiceClock serviceClock,
			@Value("${evaluation.stale-after-seconds:900}") long staleAfterSeconds,
			@Value("${evaluation.recovery-batch-size:100}") int batchSize) {
		if (staleAfterSeconds <= 0 || batchSize <= 0) {
			throw new DomainException(ErrorCode.INTERNAL_ERROR,
					"Evaluation recovery timeout and batch size must be positive.");
		}
		this.versionRepository = versionRepository;
		this.recoveryTransaction = recoveryTransaction;
		this.serviceClock = serviceClock;
		this.staleEvaluationTimeout = Duration.ofSeconds(staleAfterSeconds);
		this.batchSize = batchSize;
	}

	/**
	 * 한 회차에 처리할 수를 제한한다. 남은 대상은 다음 회차가 가져간다.
	 *
	 * <p>건별로 예외를 격리하는 이유는 회수가 비관적 잠금을 잡기 때문이다. 잠금 대기
	 * 타임아웃 하나로 나머지 대상이 통째로 밀리면 안 되고, 기동 경로에서는 ApplicationRunner가
	 * 던진 예외가 컨텍스트를 닫아 애플리케이션 기동 자체를 실패시킨다.
	 */
	public void recoverStaleEvaluations() {
		Instant staleBefore = serviceClock.now().minus(staleEvaluationTimeout);
		for (Long versionId : versionRepository.findRecoverableIds(
				EvaluationStatus.EVALUATING, staleBefore, PageRequest.of(0, batchSize))) {
			try {
				recoveryTransaction.recover(versionId);
			} catch (RuntimeException exception) {
				log.warn("evaluation_recovery_failed version_id={}", versionId, exception);
			}
		}
	}
}
