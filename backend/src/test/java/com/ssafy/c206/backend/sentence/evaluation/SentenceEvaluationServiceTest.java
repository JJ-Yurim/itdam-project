package com.ssafy.c206.backend.sentence.evaluation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import com.ssafy.c206.backend.sentence.api.SentenceVersionResponse;
import com.ssafy.c206.backend.common.api.DomainException;
import com.ssafy.c206.backend.dailyword.api.DailyWordResponse;
import com.ssafy.c206.backend.dailyword.service.DailyWordService;
import com.ssafy.c206.backend.integration.ai.AiEvaluationClient;
import com.ssafy.c206.backend.integration.ai.AiEvaluationCommand;
import com.ssafy.c206.backend.integration.ai.AiEvaluationTransientException;
import com.ssafy.c206.backend.integration.ai.AiEvaluationResult;
import com.ssafy.c206.backend.integration.ai.FakeAiEvaluationClient;
import com.ssafy.c206.backend.integration.ai.FakeAiEvaluationScenario;
import com.ssafy.c206.backend.sentence.domain.EvaluationStatus;
import com.ssafy.c206.backend.sentence.evaluation.api.SentenceEvaluationResponse;
import com.ssafy.c206.backend.sentence.evaluation.repository.SentenceEvaluationRepository;
import com.ssafy.c206.backend.sentence.evaluation.repository.SentenceMoodRepository;
import com.ssafy.c206.backend.sentence.evaluation.repository.SentenceUsedDailyWordRepository;
import com.ssafy.c206.backend.sentence.evaluation.service.SentenceEvaluationService;
import com.ssafy.c206.backend.sentence.repository.SentenceVersionRepository;
import com.ssafy.c206.backend.sentence.service.SentenceCommandService;
import com.ssafy.c206.backend.common.security.AuthenticatedUser;
import com.ssafy.c206.backend.user.service.UserFixture;
import com.ssafy.c206.backend.word.domain.Word;
import com.ssafy.c206.backend.word.etl.DictionarySense;
import com.ssafy.c206.backend.word.repository.WordRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.context.annotation.Import;

@SpringBootTest
@ActiveProfiles("test")
@Import(SentenceEvaluationServiceTest.AiClientConfig.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class SentenceEvaluationServiceTest {

	private static final AtomicInteger SEQUENCE = new AtomicInteger();

	@Autowired private SentenceEvaluationService evaluationService;
	@Autowired private SentenceCommandService sentenceCommandService;
	@Autowired private UserFixture userFixture;
	@Autowired private DailyWordService dailyWordService;
	@Autowired private WordRepository wordRepository;
	@Autowired private SentenceVersionRepository versionRepository;
	@Autowired private SentenceEvaluationRepository evaluationRepository;
	@Autowired private SentenceMoodRepository moodRepository;
	@Autowired private SentenceUsedDailyWordRepository usedDailyWordRepository;
	@Autowired private ScenarioClient aiClient;

	@BeforeEach
	void resetClient() {
		aiClient.configure(FakeAiEvaluationScenario.SUCCESS, Set.of());
	}

	@Test
	void persistsSuccessfulEvaluationAndMakesVersionImmutable() {
		AuthenticatedUser principal = principal("success");
		var version = createVersion(principal, "성공 평가 문장");
		DailyWordResponse daily = dailyWordService.getToday(principal);
		aiClient.configure(FakeAiEvaluationScenario.SUCCESS, Set.of(daily.items().get(0).wordId(), daily.items().get(1).wordId()));

		SentenceEvaluationResponse response = evaluationService.evaluate(principal, version.id());

		assertThat(response.evaluationStatus()).isEqualTo("EVALUATED");
		assertThat(response.scores().baseScore()).isEqualByComparingTo("78.35");
		assertThat(response.wordBonus()).isEqualByComparingTo("5.00");
		assertThat(response.gameBaseAttack()).isEqualByComparingTo("83.35");
		assertThat(evaluationRepository.findBySentenceVersionId(version.id())).isPresent();
		assertThat(moodRepository.findBySentenceVersionIdOrderByRankAsc(version.id())).hasSize(1);
		assertThat(usedDailyWordRepository.findDetailBySentenceVersionId(version.id())).hasSize(2);
		assertThat(versionRepository.findById(version.id()).orElseThrow().getEvaluationStatus()).isEqualTo(EvaluationStatus.EVALUATED);
		assertThatThrownBy(() -> sentenceCommandService.updateDraft(principal.userId(), version.id(), "변경 불가"))
				.isInstanceOf(DomainException.class);
		assertThatThrownBy(() -> evaluationService.evaluate(principal, version.id())).isInstanceOf(DomainException.class);
	}

	@Test
	void safetyRejectConsumesQuotaWithoutPersistingEvaluationArtifacts() {
		AuthenticatedUser principal = principal("reject");
		var version = createVersion(principal, "거절 평가 문장");
		aiClient.configure(FakeAiEvaluationScenario.SAFETY_REJECTED, Set.of());

		assertThatThrownBy(() -> evaluationService.evaluate(principal, version.id()))
				.isInstanceOf(DomainException.class)
				.extracting("errorCode").isEqualTo(com.ssafy.c206.backend.common.api.ErrorCode.POLICY_REJECTED);
		assertThat(versionRepository.findById(version.id()).orElseThrow().getEvaluationStatus()).isEqualTo(EvaluationStatus.REJECTED);
		assertThat(evaluationRepository.findBySentenceVersionId(version.id())).isEmpty();
		assertThat(moodRepository.findBySentenceVersionIdOrderByRankAsc(version.id())).isEmpty();
		assertThat(usedDailyWordRepository.findDetailBySentenceVersionId(version.id())).isEmpty();
		assertThat(evaluationService.todayUsage(principal).usedCount()).isEqualTo(1);
	}

	@Test
	void technicalFailureReleasesQuotaAndAllowsRetry() {
		AuthenticatedUser principal = principal("failure");
		var version = createVersion(principal, "실패 후 재시도 문장");
		aiClient.configure(FakeAiEvaluationScenario.TRANSIENT_FAILURE, Set.of());

		assertThatThrownBy(() -> evaluationService.evaluate(principal, version.id()))
				.isInstanceOf(DomainException.class)
				.hasCauseInstanceOf(AiEvaluationTransientException.class);
		assertThat(versionRepository.findById(version.id()).orElseThrow().getEvaluationStatus()).isEqualTo(EvaluationStatus.EVALUATION_FAILED);
		assertThat(evaluationService.todayUsage(principal).usedCount()).isZero();

		aiClient.configure(FakeAiEvaluationScenario.SUCCESS, Set.of());
		assertThat(evaluationService.evaluate(principal, version.id()).evaluationStatus()).isEqualTo("EVALUATED");
		assertThat(evaluationService.todayUsage(principal).usedCount()).isEqualTo(1);
	}

	@Test
	void invalidResponseReleasesQuota() {
		AuthenticatedUser principal = principal("invalid");
		var version = createVersion(principal, "잘못된 응답 문장");
		aiClient.configure(FakeAiEvaluationScenario.INVALID_RESPONSE, Set.of());

		assertThatThrownBy(() -> evaluationService.evaluate(principal, version.id())).isInstanceOf(DomainException.class);
		assertThat(versionRepository.findById(version.id()).orElseThrow().getEvaluationStatus()).isEqualTo(EvaluationStatus.EVALUATION_FAILED);
		assertThat(evaluationService.todayUsage(principal).usedCount()).isZero();
	}

	@Test
	void appliesZeroOneTwoAndFiveWordBonuses() {
		assertBonus(0, "0.00");
		assertBonus(1, "0.00");
		assertBonus(2, "5.00");
		assertBonus(5, "30.00");
	}

	@Test
	void enforcesFiveDailyEvaluationsForPassedAndRejectedOutcomes() {
		AuthenticatedUser principal = principal("quota");
		aiClient.configure(FakeAiEvaluationScenario.SAFETY_REJECTED, Set.of());
		for (int index = 0; index < 5; index++) {
			var version = createVersion(principal, "한도 문장 " + index);
			assertThatThrownBy(() -> evaluationService.evaluate(principal, version.id()))
					.isInstanceOf(DomainException.class);
		}
		var sixth = createVersion(principal, "여섯 번째 문장");
		assertThatThrownBy(() -> evaluationService.evaluate(principal, sixth.id()))
				.isInstanceOf(DomainException.class)
				.extracting("errorCode").isEqualTo(com.ssafy.c206.backend.common.api.ErrorCode.RATE_LIMITED);
		assertThat(evaluationService.todayUsage(principal).usedCount()).isEqualTo(5);
		assertThat(versionRepository.findById(sixth.id()).orElseThrow().getEvaluationStatus()).isEqualTo(EvaluationStatus.DRAFT);
	}

	private void assertBonus(int usedCount, String expectedBonus) {
		AuthenticatedUser principal = principal("bonus" + usedCount);
		var version = createVersion(principal, "보너스 문장 " + usedCount);
		DailyWordResponse daily = dailyWordService.getToday(principal);
		aiClient.configure(FakeAiEvaluationScenario.SUCCESS,
				daily.items().stream().limit(usedCount).map(DailyWordResponse.Item::wordId).collect(java.util.stream.Collectors.toSet()));
		assertThat(evaluationService.evaluate(principal, version.id()).wordBonus()).isEqualByComparingTo(expectedBonus);
	}

	@Test
	void blocksDuplicateRequestWhileFirstRequestIsEvaluating() throws Exception {
		AuthenticatedUser principal = principal("duplicate");
		var version = createVersion(principal, "중복 요청 문장");
		aiClient.block();
		var executor = Executors.newSingleThreadExecutor();
		try {
			var first = executor.submit(() -> evaluationService.evaluate(principal, version.id()));
			assertThat(aiClient.awaitStarted()).isTrue();
			assertThatThrownBy(() -> evaluationService.evaluate(principal, version.id())).isInstanceOf(DomainException.class);
			aiClient.release();
			assertThat(first.get(10, TimeUnit.SECONDS).evaluationStatus()).isEqualTo("EVALUATED");
		} finally {
			aiClient.release();
			executor.shutdownNow();
		}
	}

	private AuthenticatedUser principal(String name) {
		int sequence = SEQUENCE.incrementAndGet();
		var user = userFixture.createFixture(name + sequence + "@evaluation.test", "hash", name + sequence);
		seedWords(sequence);
		return new AuthenticatedUser(user.getId(), user.getEmail());
	}

	private void seedWords(int suffix) {
		for (int index = 0; index < 5; index++) {
			wordRepository.save(Word.from(DictionarySense.builder().word("eval-" + suffix + "-" + index)
					.partOfSpeech("명사").definition("평가 단어 " + suffix + "-" + index).build()));
		}
	}

	private SentenceVersionResponse createVersion(AuthenticatedUser principal, String content) {
		return sentenceCommandService.create(principal.userId(), content);
	}

	@TestConfiguration
	static class AiClientConfig {
		@Bean @Primary ScenarioClient scenarioClient() { return new ScenarioClient(); }
	}

	static class ScenarioClient implements AiEvaluationClient {
		private volatile FakeAiEvaluationScenario scenario = FakeAiEvaluationScenario.SUCCESS;
		private volatile Set<Long> usedWordIds = Set.of();
		private volatile CountDownLatch started;
		private volatile CountDownLatch release;

		void configure(FakeAiEvaluationScenario scenario, Set<Long> usedWordIds) {
			this.scenario = scenario; this.usedWordIds = Set.copyOf(usedWordIds); this.started = null; this.release = null;
		}
		void block() { this.started = new CountDownLatch(1); this.release = new CountDownLatch(1); }
		boolean awaitStarted() throws InterruptedException { return started.await(5, TimeUnit.SECONDS); }
		void release() { if (release != null) release.countDown(); }
		@Override public AiEvaluationResult evaluate(AiEvaluationCommand command) {
			if (started != null) { started.countDown(); try { release.await(10, TimeUnit.SECONDS); } catch (InterruptedException exception) { Thread.currentThread().interrupt(); throw new RuntimeException(exception); } }
			return new FakeAiEvaluationClient(scenario, usedWordIds).evaluate(command);
		}
	}
}
