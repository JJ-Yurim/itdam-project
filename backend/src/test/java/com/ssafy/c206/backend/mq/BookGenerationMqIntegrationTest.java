package com.ssafy.c206.backend.mq;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import com.ssafy.c206.backend.book.domain.AiJobStatus;
import com.ssafy.c206.backend.book.domain.BookStatus;
import com.ssafy.c206.backend.book.domain.ContentType;
import com.ssafy.c206.backend.book.repository.AiJobRepository;
import com.ssafy.c206.backend.book.repository.BookContentRepository;
import com.ssafy.c206.backend.book.repository.BookRepository;
import com.ssafy.c206.backend.book.service.BookGenerationPreparationService;
import com.ssafy.c206.backend.boss.domain.BossRun;
import com.ssafy.c206.backend.boss.repository.BossPhaseRepository;
import com.ssafy.c206.backend.boss.repository.BossRunRepository;
import com.ssafy.c206.backend.boss.service.BossFixture;
import com.ssafy.c206.backend.boss.submission.domain.BossSubmission;
import com.ssafy.c206.backend.boss.submission.repository.BossSubmissionRepository;
import com.ssafy.c206.backend.boss.submission.service.DamageCalculation;
import com.ssafy.c206.backend.common.time.ServiceClock;
import com.ssafy.c206.backend.integration.book.BookGenerationMqContract;
import com.ssafy.c206.backend.integration.book.BookGenerationRequestMessage;
import com.ssafy.c206.backend.integration.book.BookGenerationResultMessage;
import com.ssafy.c206.backend.sentence.domain.Sentence;
import com.ssafy.c206.backend.sentence.domain.SentenceVersion;
import com.ssafy.c206.backend.sentence.repository.SentenceRepository;
import com.ssafy.c206.backend.sentence.repository.SentenceVersionRepository;
import com.ssafy.c206.backend.user.domain.User;
import com.ssafy.c206.backend.user.repository.UserRepository;
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
 * Spring 쪽 MQ 계약을 실제 RabbitMQ로 고정하는 왕복 테스트. 이 테스트가 ai-service
 * 소비자(ISSUE-122)의 역할을 대행한다 — 요청 큐에서 실행 지시를 꺼내 계약을
 * 검증하고 결과 큐로 응답을 발행한다.
 *
 * <p>실제 FastAPI 소비자가 DB에서 재료를 읽어 생성하는 전체 경로는
 * MvpRealInfrastructureE2eTest(PostgreSQL + RabbitMQ + FastAPI 컨테이너)가 검증한다.
 * 소비자가 Backend DB를 직접 읽는 구조라 H2 기반인 이 테스트에서는 대행이 유일한
 * 방법이고, 대신 요청 페이로드가 재료 없이 실행 지시만 나르는 것(ADR-007)을 여기서
 * 명시적으로 고정한다.
 */
@Tag("infra")
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class BookGenerationMqIntegrationTest {

	@Container
	static final RabbitMQContainer RABBIT = new RabbitMQContainer(
			DockerImageName.parse("rabbitmq:3.13-management-alpine").asCompatibleSubstituteFor("rabbitmq"));

	@DynamicPropertySource
	static void configureRabbit(DynamicPropertyRegistry registry) {
		registry.add("book.generation.client", () -> "rabbit");
		registry.add("book.generation.dispatch-recovery-delay-ms", () -> "60000");
		// 별도 컨텍스트가 공유 H2(backend)의 테이블을 create-drop으로 지우지 않도록 전용 DB를 쓴다.
		registry.add("spring.datasource.url",
				() -> "jdbc:h2:mem:book-mq;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE");
		registry.add("spring.rabbitmq.host", RABBIT::getHost);
		registry.add("spring.rabbitmq.port", RABBIT::getAmqpPort);
		registry.add("spring.rabbitmq.username", RABBIT::getAdminUsername);
		registry.add("spring.rabbitmq.password", RABBIT::getAdminPassword);
	}

	@Autowired private BossFixture bossFixture;
	@Autowired private BossRunRepository bossRunRepository;
	@Autowired private BossPhaseRepository bossPhaseRepository;
	@Autowired private BossSubmissionRepository submissionRepository;
	@Autowired private BookGenerationPreparationService preparationService;
	@Autowired private AiJobRepository aiJobRepository;
	@Autowired private BookRepository bookRepository;
	@Autowired private BookContentRepository contentRepository;
	@Autowired private RabbitTemplate rabbitTemplate;
	@Autowired private UserRepository userRepository;
	@Autowired private SentenceRepository sentenceRepository;
	@Autowired private SentenceVersionRepository sentenceVersionRepository;
	@Autowired private ServiceClock serviceClock;

	@Test
	void publishesExecutionOrderAndAppliesConsumerResultFromBroker() throws Exception {
		Instant now = serviceClock.now();
		BossRun run = bossFixture.createThreePhaseRun(now.minusSeconds(60), now.plusSeconds(3_600), now);
		run.markCleared(now);
		run = bossRunRepository.save(run);
		BossSubmission submission = submissionFor(run, "Spring과 FastAPI 사이의 실제 MQ 계약 문장입니다.");

		var preparation = preparationService.prepareAfterClear(run.getId());
		preparationService.requestAfterCommit(preparation);
		Long jobId = preparation.request().jobId();
		var job = aiJobRepository.findById(jobId).orElseThrow();
		assertThat(job.isRequestDispatched()).isTrue();

		// 요청 메시지는 재료 없이 실행 지시만 나른다(ADR-007). 여기서부터 소비자 역할을 대행한다.
		Object received = rabbitTemplate.receiveAndConvert(BookGenerationMqContract.REQUEST_QUEUE, 10_000);
		assertThat(received).isInstanceOf(BookGenerationRequestMessage.class);
		BookGenerationRequestMessage request = (BookGenerationRequestMessage) received;
		assertThat(request.contractVersion()).isEqualTo(BookGenerationMqContract.REQUEST_CONTRACT_VERSION);
		assertThat(request.jobId()).isEqualTo(jobId);
		assertThat(request.correlationId()).isEqualTo(job.getCorrelationId());

		// 소비자가 DB에서 재료를 읽어 생성한 결과를 발행했다고 치고 결과 큐로 응답한다.
		rabbitTemplate.convertAndSend(BookGenerationMqContract.EXCHANGE, BookGenerationMqContract.RESULT_QUEUE,
				successMessage(jobId, request.correlationId(), submission));
		awaitJobStatus(jobId, AiJobStatus.SUCCESS);

		var book = bookRepository.findByBossRunId(run.getId()).orElseThrow();
		assertThat(aiJobRepository.findById(jobId).orElseThrow().getModelName()).isEqualTo("contract-provider");
		assertThat(book.getStatus()).isEqualTo(BookStatus.COMPLETED);
		assertThat(contentRepository.findDetailsByBookIdIn(List.of(book.getId())))
				.satisfiesExactly(
						content -> assertThat(content.getContentType()).isEqualTo(ContentType.AI_GENERATED),
						content -> {
							assertThat(content.getContentType()).isEqualTo(ContentType.USER_ORIGINAL);
							assertThat(content.getSourceSubmission().getId()).isEqualTo(submission.getId());
							assertThat(content.getContent()).isEqualTo("Spring과 FastAPI 사이의 실제 MQ 계약 문장입니다.");
						});

		// 같은 방향의 중복 결과는 멱등 처리되어 본문이 중복 생성되지 않는다.
		rabbitTemplate.convertAndSend(BookGenerationMqContract.EXCHANGE, BookGenerationMqContract.RESULT_QUEUE,
				successMessage(jobId, request.correlationId(), submission));
		Thread.sleep(2_000);
		assertThat(contentRepository.countByBookId(book.getId())).isEqualTo(2);
		assertThat(rabbitTemplate.receive(BookGenerationMqContract.REQUEST_DLQ, 500)).isNull();
		assertThat(rabbitTemplate.receive(BookGenerationMqContract.RESULT_DLQ, 500)).isNull();
	}

	private BookGenerationResultMessage successMessage(Long jobId, String correlationId, BossSubmission submission) {
		return new BookGenerationResultMessage(BookGenerationMqContract.RESULT_CONTRACT_VERSION, jobId, correlationId,
				true, null, null,
				List.of(
						new BookGenerationResultMessage.ContentBlock(1, "AI_GENERATED",
								"Spring과 FastAPI 계약이 정상적으로 연결되었습니다.", null),
						new BookGenerationResultMessage.ContentBlock(2, "USER_ORIGINAL",
								"Spring과 FastAPI 사이의 실제 MQ 계약 문장입니다.", submission.getId())),
				// body는 블록 원문을 그대로 담아야 저장 단계 검증을 통과한다.
				"Spring과 FastAPI 계약이 정상적으로 연결되었습니다.\n\nSpring과 FastAPI 사이의 실제 MQ 계약 문장입니다.",
				"MQ 왕복 검증용 요약입니다.",
				List.of(submission.getId()), List.of(), "contract-provider", "contract-book-prompt-v1");
	}

	private BossSubmission submissionFor(BossRun run, String text) {
		Instant now = serviceClock.now();
		var phase = bossPhaseRepository.findDetailsByBossRunIdIn(List.of(run.getId())).getFirst();
		User user = userRepository.save(User.createFixture(
				"book-mq-" + System.nanoTime() + "@test.local", "hash", "book-mq-author"));
		Sentence sentence = sentenceRepository.save(Sentence.create(user, now));
		SentenceVersion version = sentenceVersionRepository.save(SentenceVersion.draft(sentence, 1, text, now));
		DamageCalculation damage = new DamageCalculation(new BigDecimal("90"), BigDecimal.ZERO,
				new BigDecimal("90"), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ONE, 90L);
		return submissionRepository.save(
				BossSubmission.create(run, phase, user, version, serviceClock.today(), damage, now));
	}

	private void awaitJobStatus(Long jobId, AiJobStatus expected) throws InterruptedException {
		long deadline = System.currentTimeMillis() + 15_000;
		while (System.currentTimeMillis() < deadline) {
			if (aiJobRepository.findById(jobId).orElseThrow().getStatus() == expected) {
				return;
			}
			Thread.sleep(100);
		}
		assertThat(aiJobRepository.findById(jobId).orElseThrow().getStatus()).isEqualTo(expected);
	}
}
