package com.ssafy.c206.backend.mq;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import java.util.stream.Collectors;

import com.ssafy.c206.backend.common.security.AuthenticatedUser;
import com.ssafy.c206.backend.integration.ai.AiEvaluationCommand;
import com.ssafy.c206.backend.integration.ai.AiEvaluationResult;
import com.ssafy.c206.backend.integration.ai.DailyWordCard;
import com.ssafy.c206.backend.integration.ai.FakeAiEvaluationClient;
import com.ssafy.c206.backend.integration.ai.FakeAiEvaluationScenario;
import com.ssafy.c206.backend.integration.evaluation.SentenceEvaluationMqContract;
import com.ssafy.c206.backend.integration.evaluation.SentenceEvaluationRequestMessage;
import com.ssafy.c206.backend.integration.evaluation.SentenceEvaluationResultMessage;
import com.ssafy.c206.backend.sentence.domain.EvaluationStatus;
import com.ssafy.c206.backend.sentence.evaluation.repository.SentenceEvaluationRepository;
import com.ssafy.c206.backend.sentence.evaluation.service.SentenceEvaluationService;
import com.ssafy.c206.backend.sentence.repository.SentenceVersionRepository;
import com.ssafy.c206.backend.sentence.service.SentenceCommandService;
import com.ssafy.c206.backend.user.service.UserFixture;
import com.ssafy.c206.backend.word.domain.Word;
import com.ssafy.c206.backend.word.etl.DictionarySense;
import com.ssafy.c206.backend.word.repository.WordRepository;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * 문장 평가 MQ 계약을 실제 RabbitMQ로 고정하는 왕복 테스트(ISSUE-208). ai-service
 * 소비자의 역할을 대행해 요청 큐에서 페이로드를 검증하고 결과 큐로 응답한다.
 *
 * <p>책 생성(ADR-007, 클레임 체크)과 달리 평가 요청은 재료(문장·오늘의 단어 5개)를
 * 메시지가 직접 나른다 — 페이로드가 1~2KB라 소비자의 DB 접근을 없애는 쪽이 싸다.
 * 이 차이를 여기서 명시적으로 고정한다.
 */
@Tag("infra")
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class SentenceEvaluationMqIntegrationTest {

	@Container
	static final RabbitMQContainer RABBIT = new RabbitMQContainer(
			DockerImageName.parse("rabbitmq:3.13-management-alpine").asCompatibleSubstituteFor("rabbitmq"));

	@DynamicPropertySource
	static void configureRabbit(DynamicPropertyRegistry registry) {
		registry.add("evaluation.dispatch", () -> "rabbit");
		// 별도 컨텍스트가 공유 H2의 테이블을 create-drop으로 지우지 않도록 전용 DB를 쓴다.
		registry.add("spring.datasource.url",
				() -> "jdbc:h2:mem:evaluation-mq;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE");
		registry.add("spring.rabbitmq.host", RABBIT::getHost);
		registry.add("spring.rabbitmq.port", RABBIT::getAmqpPort);
		registry.add("spring.rabbitmq.username", RABBIT::getAdminUsername);
		registry.add("spring.rabbitmq.password", RABBIT::getAdminPassword);
	}

	@Autowired private SentenceEvaluationService evaluationService;
	@Autowired private SentenceCommandService sentenceCommandService;
	@Autowired private UserFixture userFixture;
	@Autowired private WordRepository wordRepository;
	@Autowired private SentenceVersionRepository versionRepository;
	@Autowired private SentenceEvaluationRepository evaluationRepository;
	@Autowired private RabbitTemplate rabbitTemplate;

	@Test
	void publishesMaterialCarryingRequestAndAppliesConsumerResultFromBroker() throws Exception {
		AuthenticatedUser principal = principal();
		var version = sentenceCommandService.create(principal.userId(), "실제 브로커를 지나는 평가 문장입니다.");

		var response = evaluationService.evaluate(principal, version.id());
		assertThat(response.evaluationStatus()).isEqualTo("EVALUATING");
		assertThat(versionRepository.findById(version.id()).orElseThrow().isEvaluationDispatched()).isTrue();

		// 여기서부터 ai-service 소비자 역할을 대행한다. 재료가 메시지에 실려 와야 한다.
		Object received = rabbitTemplate.receiveAndConvert(SentenceEvaluationMqContract.REQUEST_QUEUE, 10_000);
		assertThat(received).isInstanceOf(SentenceEvaluationRequestMessage.class);
		SentenceEvaluationRequestMessage request = (SentenceEvaluationRequestMessage) received;
		assertThat(request.contractVersion()).isEqualTo(SentenceEvaluationMqContract.REQUEST_CONTRACT_VERSION);
		assertThat(request.sentenceVersionId()).isEqualTo(version.id());
		assertThat(request.text()).isEqualTo("실제 브로커를 지나는 평가 문장입니다.");
		assertThat(request.dailyWords()).hasSize(5);

		rabbitTemplate.convertAndSend(SentenceEvaluationMqContract.EXCHANGE,
				SentenceEvaluationMqContract.RESULT_QUEUE, successMessage(request));
		awaitStatus(version.id(), EvaluationStatus.EVALUATED);
		assertThat(evaluationRepository.findBySentenceVersionId(version.id())).isPresent();

		// 같은 결과의 중복 전달은 멱등 처리되어 DLQ로 새지 않는다.
		rabbitTemplate.convertAndSend(SentenceEvaluationMqContract.EXCHANGE,
				SentenceEvaluationMqContract.RESULT_QUEUE, successMessage(request));
		Thread.sleep(2_000);
		assertThat(rabbitTemplate.receive(SentenceEvaluationMqContract.REQUEST_DLQ, 500)).isNull();
		assertThat(rabbitTemplate.receive(SentenceEvaluationMqContract.RESULT_DLQ, 500)).isNull();
	}

	private SentenceEvaluationResultMessage successMessage(SentenceEvaluationRequestMessage request) {
		Set<Long> usedIds = request.dailyWords().stream().limit(2)
				.map(DailyWordCard::wordId).collect(Collectors.toSet());
		AiEvaluationCommand command = new AiEvaluationCommand(request.sentenceVersionId(), request.text(),
				request.serviceDate(), request.dailyWords());
		AiEvaluationResult evaluation = new FakeAiEvaluationClient(FakeAiEvaluationScenario.SUCCESS, usedIds)
				.evaluate(command);
		return new SentenceEvaluationResultMessage(SentenceEvaluationMqContract.RESULT_CONTRACT_VERSION,
				request.correlationId(), request.sentenceVersionId(), true, evaluation, null, null);
	}

	private AuthenticatedUser principal() {
		var user = userFixture.createFixture("evaluation-mq-" + System.nanoTime() + "@test.local",
				"hash", "evaluation-mq");
		for (int index = 0; index < 5; index++) {
			wordRepository.save(Word.from(DictionarySense.builder().word("mq-word-" + System.nanoTime() + "-" + index)
					.partOfSpeech("명사").definition("MQ 왕복 검증 단어 " + index).build()));
		}
		return new AuthenticatedUser(user.getId(), user.getEmail());
	}

	private void awaitStatus(Long versionId, EvaluationStatus expected) throws InterruptedException {
		long deadline = System.currentTimeMillis() + 15_000;
		while (System.currentTimeMillis() < deadline) {
			if (versionRepository.findById(versionId).orElseThrow().getEvaluationStatus() == expected) {
				return;
			}
			Thread.sleep(100);
		}
		assertThat(versionRepository.findById(versionId).orElseThrow().getEvaluationStatus()).isEqualTo(expected);
	}
}
