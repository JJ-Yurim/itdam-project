package com.ssafy.c206.backend.sentence.evaluation;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;

import com.ssafy.c206.backend.common.time.ServiceClock;
import com.ssafy.c206.backend.sentence.domain.EvaluationStatus;
import com.ssafy.c206.backend.sentence.domain.Sentence;
import com.ssafy.c206.backend.sentence.domain.SentenceVersion;
import com.ssafy.c206.backend.sentence.evaluation.service.EvaluationRecoveryService;
import com.ssafy.c206.backend.sentence.evaluation.service.EvaluationRecoveryTransaction;
import com.ssafy.c206.backend.sentence.repository.SentenceRepository;
import com.ssafy.c206.backend.sentence.repository.SentenceVersionRepository;
import com.ssafy.c206.backend.user.domain.User;
import com.ssafy.c206.backend.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

/**
 * 재기동 복구(EvaluationRecoveryTransaction)의 판정 기준을 고정하는 특성화 테스트.
 * stale 판정 시각과 상태 전이 규칙이 리팩터링 후에도 유지되어야 한다.
 */
@SpringBootTest
@ActiveProfiles("test")
class EvaluationRecoveryTransactionTest {

	private static final AtomicInteger SEQUENCE = new AtomicInteger();
	private static final long STALE_AFTER_SECONDS = 900;

	@Autowired private EvaluationRecoveryTransaction recoveryTransaction;
	@Autowired private EvaluationRecoveryService recoveryService;
	@Autowired private SentenceRepository sentenceRepository;
	@Autowired private SentenceVersionRepository versionRepository;
	@Autowired private UserRepository userRepository;
	@Autowired private ServiceClock serviceClock;

	@Test
	@Transactional
	void staleEvaluatingVersionIsRecoveredToEvaluationFailed() {
		Instant staleStart = serviceClock.now().minusSeconds(STALE_AFTER_SECONDS + 60);
		SentenceVersion version = evaluatingVersion(staleStart);

		recoveryTransaction.recover(version.getId());

		assertThat(versionRepository.findById(version.getId()).orElseThrow().getEvaluationStatus())
				.isEqualTo(EvaluationStatus.EVALUATION_FAILED);
	}

	@Test
	@Transactional
	void freshEvaluatingVersionIsLeftUntouched() {
		SentenceVersion version = evaluatingVersion(serviceClock.now());

		recoveryTransaction.recover(version.getId());

		assertThat(versionRepository.findById(version.getId()).orElseThrow().getEvaluationStatus())
				.isEqualTo(EvaluationStatus.EVALUATING);
	}

	@Test
	@Transactional
	void nonEvaluatingVersionIsIgnored() {
		SentenceVersion draft = draftVersion();

		recoveryTransaction.recover(draft.getId());

		assertThat(versionRepository.findById(draft.getId()).orElseThrow().getEvaluationStatus())
				.isEqualTo(EvaluationStatus.DRAFT);
	}

	@Test
	@Transactional
	void unknownVersionIsIgnoredWithoutError() {
		recoveryTransaction.recover(Long.MAX_VALUE);
	}

	@Test
	@Transactional
	void staleEvaluationsAreRecoveredWithoutRestart() {
		// 기동 시 1회만 돌면 운영 중 생긴 고착이 다음 재배포까지 남는다. 그동안 그 문장은
		// 화면에서 "평가 진행 중"으로 잠긴 채 손댈 수 없다.
		SentenceVersion stale = evaluatingVersion(serviceClock.now().minusSeconds(STALE_AFTER_SECONDS + 60));
		SentenceVersion fresh = evaluatingVersion(serviceClock.now());

		recoveryService.recoverStaleEvaluations();

		assertThat(versionRepository.findById(stale.getId()).orElseThrow().getEvaluationStatus())
				.isEqualTo(EvaluationStatus.EVALUATION_FAILED);
		// 진행 중인 평가를 가로채면 정상 요청이 실패로 뒤집힌다.
		assertThat(versionRepository.findById(fresh.getId()).orElseThrow().getEvaluationStatus())
				.isEqualTo(EvaluationStatus.EVALUATING);
	}

	private SentenceVersion draftVersion() {
		Instant now = serviceClock.now();
		int suffix = SEQUENCE.incrementAndGet();
		User user = userRepository.save(
				User.createFixture("recovery" + suffix + "@evaluation.test", "hash", "recovery" + suffix));
		Sentence sentence = sentenceRepository.save(Sentence.create(user, now));
		return versionRepository.save(SentenceVersion.draft(sentence, 1, "복구 검증 문장 " + suffix, now));
	}

	private SentenceVersion evaluatingVersion(Instant startedAt) {
		SentenceVersion version = draftVersion();
		version.startEvaluation(startedAt);
		return versionRepository.save(version);
	}
}
