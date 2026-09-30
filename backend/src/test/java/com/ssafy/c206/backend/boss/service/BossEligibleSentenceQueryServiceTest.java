package com.ssafy.c206.backend.boss.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import com.ssafy.c206.backend.boss.domain.Boss;
import com.ssafy.c206.backend.boss.domain.BossPhase;
import com.ssafy.c206.backend.boss.domain.BossRun;
import com.ssafy.c206.backend.boss.domain.BossRunStatus;
import com.ssafy.c206.backend.boss.repository.BossMoodRepository;
import com.ssafy.c206.backend.boss.repository.BossPhaseRepository;
import com.ssafy.c206.backend.boss.repository.BossRunRepository;
import com.ssafy.c206.backend.boss.submission.repository.BossSubmissionRepository;
import com.ssafy.c206.backend.boss.submission.service.DamageCalculation;
import com.ssafy.c206.backend.boss.submission.service.DamageCalculator;
import com.ssafy.c206.backend.common.security.AuthenticatedUser;
import com.ssafy.c206.backend.common.time.ServiceClock;
import com.ssafy.c206.backend.sentence.domain.Sentence;
import com.ssafy.c206.backend.sentence.domain.SentenceVersion;
import com.ssafy.c206.backend.sentence.evaluation.domain.SentenceEvaluation;
import com.ssafy.c206.backend.sentence.evaluation.service.EligibleSentenceRevision;
import com.ssafy.c206.backend.sentence.evaluation.service.EvaluatedSentenceQueryService;
import com.ssafy.c206.backend.user.service.UserQueryService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BossEligibleSentenceQueryServiceTest {

	@Mock private BossRunLifecycleService lifecycleService;
	@Mock private BossRunRepository bossRunRepository;
	@Mock private BossPhaseRepository bossPhaseRepository;
	@Mock private BossMoodRepository bossMoodRepository;
	@Mock private BossSubmissionRepository submissionRepository;
	@Mock private EvaluatedSentenceQueryService evaluatedSentenceQueryService;
	@Mock private DamageCalculator damageCalculator;
	@Mock private UserQueryService userQueryService;
	@Mock private ServiceClock serviceClock;

	@InjectMocks private BossEligibleSentenceQueryService service;

	@Test
	void sortsAndPagesSentencesByBossAdjustedEnergyDescending() {
		AuthenticatedUser principal = new AuthenticatedUser(7L, "writer@example.com");
		BossRun run = mock(BossRun.class);
		Boss boss = mock(Boss.class);
		BossPhase phase = mock(BossPhase.class);
		SentenceEvaluation weaker = evaluation(101L, 201L, "낮은 에너지 문장");
		SentenceEvaluation stronger = evaluation(102L, 202L, "높은 에너지 문장");

		when(bossRunRepository.findWithBossById(17L)).thenReturn(java.util.Optional.of(run));
		when(run.getStatus()).thenReturn(BossRunStatus.ACTIVE);
		when(run.getId()).thenReturn(17L);
		when(run.getCurrentPhaseNo()).thenReturn(1);
		when(run.getBoss()).thenReturn(boss);
		when(boss.getId()).thenReturn(3L);
		when(bossPhaseRepository.findCurrentByBossRunId(17L, 1)).thenReturn(java.util.Optional.of(phase));
		// 최소 점수가 없는 정상 설정도 0점 기준으로 계산해야 한다.
		when(phase.getHardMinScore()).thenReturn(null);
		when(phase.getPhaseNo()).thenReturn(1);
		when(phase.getRecommendedScore()).thenReturn(BigDecimal.valueOf(60));
		when(phase.getDamageMultiplier()).thenReturn(BigDecimal.ONE);
		when(submissionRepository.findSubmittedSentenceVersionIds(17L, 7L)).thenReturn(List.of());
		when(evaluatedSentenceQueryService.getEligibleRevision(7L))
				.thenReturn(new EligibleSentenceRevision(2, Instant.parse("2026-08-08T00:00:00Z")));
		when(evaluatedSentenceQueryService.getAllEligibleForSubmission(7L, List.of()))
				.thenReturn(List.of(weaker, stronger));
		when(evaluatedSentenceQueryService.getMoodsForVersions(List.of(101L, 102L))).thenReturn(List.of());
		when(bossMoodRepository.findDetailsByBossIdIn(List.of(3L))).thenReturn(List.of());
		when(damageCalculator.calculate(any()))
				.thenReturn(calculation(40), calculation(90));

		var result = service.getEligible(principal, 17L, 0, 1);

		assertThat(result.content()).singleElement()
				.satisfies(sentence -> {
					assertThat(sentence.sentenceVersionId()).isEqualTo(102L);
					assertThat(sentence.sentenceEnergy()).isEqualTo(90);
				});
		assertThat(result.totalElements()).isEqualTo(2);
		assertThat(result.totalPages()).isEqualTo(2);
		assertThat(result.hasNext()).isTrue();

		var secondPage = service.getEligible(principal, 17L, 1, 1);
		assertThat(secondPage.content()).singleElement()
				.satisfies(sentence -> assertThat(sentence.sentenceVersionId()).isEqualTo(101L));
		verify(evaluatedSentenceQueryService, times(1)).getAllEligibleForSubmission(7L, List.of());
		verify(damageCalculator, times(2)).calculate(any());
	}

	private SentenceEvaluation evaluation(long versionId, long sentenceId, String content) {
		SentenceEvaluation evaluation = mock(SentenceEvaluation.class);
		SentenceVersion version = mock(SentenceVersion.class);
		Sentence sentence = mock(Sentence.class);
		when(evaluation.getSentenceVersion()).thenReturn(version);
		when(evaluation.getBaseScore()).thenReturn(BigDecimal.valueOf(50));
		when(evaluation.getWordBonus()).thenReturn(BigDecimal.ZERO);
		when(evaluation.getGameBaseAttack()).thenReturn(BigDecimal.valueOf(50));
		when(version.getId()).thenReturn(versionId);
		when(version.getSentence()).thenReturn(sentence);
		when(version.getVersionNo()).thenReturn(1);
		when(version.getContent()).thenReturn(content);
		when(sentence.getId()).thenReturn(sentenceId);
		return evaluation;
	}

	private DamageCalculation calculation(long finalDamage) {
		return new DamageCalculation(BigDecimal.valueOf(50), BigDecimal.ZERO, BigDecimal.valueOf(50),
				BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ONE, finalDamage);
	}
}
