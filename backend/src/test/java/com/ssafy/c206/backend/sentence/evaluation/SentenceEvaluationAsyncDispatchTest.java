package com.ssafy.c206.backend.sentence.evaluation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

import com.ssafy.c206.backend.common.security.AuthenticatedUser;
import com.ssafy.c206.backend.integration.ai.AiEvaluationCommand;
import com.ssafy.c206.backend.integration.ai.AiEvaluationResult;
import com.ssafy.c206.backend.integration.ai.DailyWordCard;
import com.ssafy.c206.backend.integration.ai.FakeAiEvaluationClient;
import com.ssafy.c206.backend.integration.ai.FakeAiEvaluationScenario;
import com.ssafy.c206.backend.integration.evaluation.SentenceEvaluationMqContract;
import com.ssafy.c206.backend.integration.evaluation.SentenceEvaluationRequestMessage;
import com.ssafy.c206.backend.integration.evaluation.SentenceEvaluationRequester;
import com.ssafy.c206.backend.integration.evaluation.SentenceEvaluationResultMessage;
import com.ssafy.c206.backend.sentence.api.SentenceVersionResponse;
import com.ssafy.c206.backend.sentence.domain.EvaluationStatus;
import com.ssafy.c206.backend.sentence.evaluation.api.SentenceEvaluationResponse;
import com.ssafy.c206.backend.sentence.evaluation.repository.SentenceEvaluationRepository;
import com.ssafy.c206.backend.sentence.evaluation.repository.SentenceUsedDailyWordRepository;
import com.ssafy.c206.backend.sentence.evaluation.service.EvaluationDispatchScheduler;
import com.ssafy.c206.backend.sentence.evaluation.service.EvaluationResultApplyService;
import com.ssafy.c206.backend.sentence.evaluation.service.SentenceEvaluationService;
import com.ssafy.c206.backend.sentence.repository.SentenceVersionRepository;
import com.ssafy.c206.backend.sentence.service.SentenceCommandService;
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
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

/**
 * rabbit 모드 접수-발행-결과 반영을 브로커 없이 검증한다(ISSUE-208). 발행 경계는
 * 가짜 requester가 대신하고, 실제 브로커 왕복 계약은 infra 태그의
 * SentenceEvaluationMqIntegrationTest가 고정한다.
 */
@SpringBootTest(properties = {
		"evaluation.dispatch=rabbit",
		// 브로커가 없으므로 결과 리스너 컨테이너가 연결을 시도하지 않게 한다.
		"spring.rabbitmq.listener.simple.auto-startup=false",
		// 릴레이는 각 테스트가 수동으로 돌린다. 백그라운드 실행이 발행 실패 시나리오를 오염시키면 안 된다.
		"evaluation.dispatch-delay-ms=3600000",
		// 방금 접수한 행을 곧바로 릴레이 대상으로 만들기 위해 유예를 없앤다.
		"evaluation.dispatch-grace-seconds=0"})
@ActiveProfiles("test")
@Import(SentenceEvaluationAsyncDispatchTest.RequesterConfig.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class SentenceEvaluationAsyncDispatchTest {

	@Autowired private SentenceEvaluationService evaluationService;
	@Autowired private EvaluationResultApplyService applyService;
	@Autowired private EvaluationDispatchScheduler dispatchScheduler;
	@Autowired private SentenceCommandService sentenceCommandService;
	@Autowired private UserFixture userFixture;
	@Autowired private WordRepository wordRepository;
	@Autowired private SentenceVersionRepository versionRepository;
	@Autowired private SentenceEvaluationRepository evaluationRepository;
	@Autowired private SentenceUsedDailyWordRepository usedDailyWordRepository;
	@Autowired private RecordingRequester requester;

	private static int sequence;

	@BeforeEach
	void resetRequester() {
		requester.published.clear();
		requester.failing = false;
	}

	@Test
	void acceptReturnsEvaluatingAndPublishesMaterialsInMessage() {
		AuthenticatedUser principal = principal("accept");
		var version = createVersion(principal, "비동기 접수 문장");

		SentenceEvaluationResponse response = evaluationService.evaluate(principal, version.id());

		assertThat(response.evaluationStatus()).isEqualTo("EVALUATING");
		assertThat(response.scores()).isNull();
		assertThat(evaluationService.todayUsage(principal).usedCount()).isEqualTo(1);

		SentenceEvaluationRequestMessage request = requester.published.getLast();
		assertThat(request.contractVersion()).isEqualTo(SentenceEvaluationMqContract.REQUEST_CONTRACT_VERSION);
		assertThat(request.sentenceVersionId()).isEqualTo(version.id());
		assertThat(request.text()).isEqualTo("비동기 접수 문장");
		// 클레임 체크 없이 재료를 메시지가 직접 나른다 — 컨슈머는 DB에 접근하지 않는다.
		assertThat(request.dailyWords()).hasSize(5);
		var persisted = versionRepository.findById(version.id()).orElseThrow();
		assertThat(persisted.isEvaluationDispatched()).isTrue();
		assertThat(persisted.getEvaluationCorrelationId()).isEqualTo(request.correlationId());
	}

	@Test
	void successResultCompletesEvaluationAndDuplicateIsDropped() {
		AuthenticatedUser principal = principal("success");
		var version = createVersion(principal, "성공 결과 문장");
		evaluationService.evaluate(principal, version.id());
		SentenceEvaluationRequestMessage request = requester.published.getLast();
		SentenceEvaluationResultMessage result = successResult(request, 2);

		applyService.apply(result);

		assertThat(versionRepository.findById(version.id()).orElseThrow().getEvaluationStatus())
				.isEqualTo(EvaluationStatus.EVALUATED);
		assertThat(evaluationRepository.findBySentenceVersionId(version.id())).isPresent();
		assertThat(usedDailyWordRepository.findDetailBySentenceVersionId(version.id())).hasSize(2);
		assertThat(evaluationService.get(principal, version.id()).evaluationStatus()).isEqualTo("EVALUATED");

		// 중복 전달은 종결 상태에서 조용히 버려진다 — 예외 없이, 산출물 중복 없이.
		applyService.apply(result);
		assertThat(usedDailyWordRepository.findDetailBySentenceVersionId(version.id())).hasSize(2);
	}

	@Test
	void rejectedResultMarksRejectedAndKeepsQuota() {
		AuthenticatedUser principal = principal("reject");
		var version = createVersion(principal, "거절 결과 문장");
		evaluationService.evaluate(principal, version.id());
		SentenceEvaluationRequestMessage request = requester.published.getLast();

		applyService.apply(resultOf(request, FakeAiEvaluationScenario.SAFETY_REJECTED, Set.of()));

		assertThat(versionRepository.findById(version.id()).orElseThrow().getEvaluationStatus())
				.isEqualTo(EvaluationStatus.REJECTED);
		assertThat(evaluationRepository.findBySentenceVersionId(version.id())).isEmpty();
		assertThat(evaluationService.todayUsage(principal).usedCount()).isEqualTo(1);
	}

	@Test
	void failureResultReleasesQuotaAndStaleCorrelationIsFenced() {
		AuthenticatedUser principal = principal("failure");
		var version = createVersion(principal, "실패 후 재시도 문장");
		evaluationService.evaluate(principal, version.id());
		SentenceEvaluationRequestMessage firstRequest = requester.published.getLast();

		applyService.apply(new SentenceEvaluationResultMessage(
				SentenceEvaluationMqContract.RESULT_CONTRACT_VERSION, firstRequest.correlationId(),
				version.id(), false, null, "AI_EVALUATION_FAILED", "GMS timeout"));

		assertThat(versionRepository.findById(version.id()).orElseThrow().getEvaluationStatus())
				.isEqualTo(EvaluationStatus.EVALUATION_FAILED);
		assertThat(evaluationService.todayUsage(principal).usedCount()).isZero();

		// 재시도가 새 상관 ID를 발급하면 이전 시도의 늦은 결과는 펜싱된다.
		evaluationService.evaluate(principal, version.id());
		SentenceEvaluationRequestMessage secondRequest = requester.published.getLast();
		assertThat(secondRequest.correlationId()).isNotEqualTo(firstRequest.correlationId());

		applyService.apply(successResult(firstRequest, 2));
		assertThat(versionRepository.findById(version.id()).orElseThrow().getEvaluationStatus())
				.isEqualTo(EvaluationStatus.EVALUATING);

		applyService.apply(successResult(secondRequest, 2));
		assertThat(versionRepository.findById(version.id()).orElseThrow().getEvaluationStatus())
				.isEqualTo(EvaluationStatus.EVALUATED);
		assertThat(evaluationService.todayUsage(principal).usedCount()).isEqualTo(1);
	}

	@Test
	void publishFailureKeepsAcceptanceAndRelayCompensates() {
		AuthenticatedUser principal = principal("relay");
		var version = createVersion(principal, "릴레이 보상 문장");
		requester.failing = true;

		SentenceEvaluationResponse response = evaluationService.evaluate(principal, version.id());

		// 발행이 죽어도 접수는 이미 확정이다. 사용자는 폴링으로 기다리고 릴레이가 발행을 보상한다.
		assertThat(response.evaluationStatus()).isEqualTo("EVALUATING");
		assertThat(requester.published).isEmpty();
		assertThat(versionRepository.findById(version.id()).orElseThrow().isEvaluationDispatched()).isFalse();

		requester.failing = false;
		dispatchScheduler.relayUndispatched();

		assertThat(requester.published).hasSize(1);
		assertThat(requester.published.getLast().sentenceVersionId()).isEqualTo(version.id());
		assertThat(versionRepository.findById(version.id()).orElseThrow().isEvaluationDispatched()).isTrue();
	}

	@Test
	void unsupportedResultContractIsRetriedNotSwallowed() {
		assertThatThrownBy(() -> applyService.apply(new SentenceEvaluationResultMessage(
				"sentence-evaluation-mq-v999", java.util.UUID.randomUUID(), 1L, false, null, "X", "unknown")))
				.isInstanceOf(IllegalStateException.class);
	}

	private SentenceEvaluationResultMessage successResult(SentenceEvaluationRequestMessage request, int usedWords) {
		Set<Long> usedIds = request.dailyWords().stream().limit(usedWords)
				.map(DailyWordCard::wordId).collect(Collectors.toSet());
		return resultOf(request, FakeAiEvaluationScenario.SUCCESS, usedIds);
	}

	private SentenceEvaluationResultMessage resultOf(SentenceEvaluationRequestMessage request,
			FakeAiEvaluationScenario scenario, Set<Long> usedWordIds) {
		AiEvaluationCommand command = new AiEvaluationCommand(request.sentenceVersionId(), request.text(),
				request.serviceDate(), request.dailyWords());
		AiEvaluationResult evaluation = new FakeAiEvaluationClient(scenario, usedWordIds).evaluate(command);
		return new SentenceEvaluationResultMessage(SentenceEvaluationMqContract.RESULT_CONTRACT_VERSION,
				request.correlationId(), request.sentenceVersionId(), true, evaluation, null, null);
	}

	private AuthenticatedUser principal(String name) {
		int suffix = ++sequence;
		var user = userFixture.createFixture(name + suffix + "@async-dispatch.test", "hash", name + suffix);
		for (int index = 0; index < 5; index++) {
			wordRepository.save(Word.from(DictionarySense.builder().word("async-" + suffix + "-" + index)
					.partOfSpeech("명사").definition("비동기 평가 단어 " + suffix + "-" + index).build()));
		}
		return new AuthenticatedUser(user.getId(), user.getEmail());
	}

	private SentenceVersionResponse createVersion(AuthenticatedUser principal, String content) {
		return sentenceCommandService.create(principal.userId(), content);
	}

	@TestConfiguration
	static class RequesterConfig {
		@Bean @Primary RecordingRequester recordingRequester() { return new RecordingRequester(); }
	}

	static class RecordingRequester implements SentenceEvaluationRequester {
		final List<SentenceEvaluationRequestMessage> published = new CopyOnWriteArrayList<>();
		volatile boolean failing;

		@Override
		public void request(SentenceEvaluationRequestMessage message) {
			if (failing) {
				throw new IllegalStateException("simulated broker outage");
			}
			published.add(message);
		}
	}
}
