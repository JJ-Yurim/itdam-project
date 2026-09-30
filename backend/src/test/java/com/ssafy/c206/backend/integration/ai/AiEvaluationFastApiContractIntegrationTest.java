package com.ssafy.c206.backend.integration.ai;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Tag("infra")
@SpringBootTest(properties = "ai.evaluation.client=http")
@ActiveProfiles("test")
@Testcontainers
class AiEvaluationFastApiContractIntegrationTest {

	private static final int AI_SERVICE_PORT = 8001;

	@Container
	static final GenericContainer<?> AI_SERVICE = createAiServiceContainer();

	@Autowired
	private AiEvaluationClient client;

	@DynamicPropertySource
	static void configureAiService(DynamicPropertyRegistry registry) {
		registry.add("ai.evaluation.base-url", () -> "http://" + AI_SERVICE.getHost()
			+ ":" + AI_SERVICE.getMappedPort(AI_SERVICE_PORT));
		registry.add("ai.evaluation.contract-version", () -> "sentence-evaluation-v1");
	}

	@Test
	void exchangesPassedEvaluationWithRealFastApiApplication() {
		AiEvaluationResult result = client.evaluate(command("contract-passed"));

		assertThat(result.safety().status()).isEqualTo(SafetyStatus.PASSED);
		assertThat(result.scores().completeness()).isEqualTo(81);
		assertThat(result.overallFeedback()).contains("Spring과 FastAPI");
		assertThat(result.moods()).containsExactly(new MoodResult("HOPE", new BigDecimal("0.88"), 1));
		assertThat(result.usedDailyWords()).containsExactly(
			new UsedDailyWordResult(101, true),
			new UsedDailyWordResult(102, false),
			new UsedDailyWordResult(103, false),
			new UsedDailyWordResult(104, false),
			new UsedDailyWordResult(105, false)
		);
		assertThat(result.modelName()).isEqualTo("contract-provider");
		assertThat(result.embedding()).hasSize(768);
		assertThat(result.embedding().getFirst()).isEqualTo(1.0);
		assertThat(result.embeddingModel()).isEqualTo("contract-embedding-v1");
		assertThat(result.embeddingDimension()).isEqualTo(768);
	}

	@Test
	void exchangesRejectedEvaluationWithRealFastApiApplication() {
		AiEvaluationResult result = client.evaluate(command("contract-rejected"));

		assertThat(result.safety().status()).isEqualTo(SafetyStatus.REJECTED);
		assertThat(result.safety().reason()).isEqualTo("contract safety rejection");
		assertThat(result.scores()).isNull();
		assertThat(result.overallFeedback()).isNull();
		assertThat(result.moods()).isEmpty();
		assertThat(result.usedDailyWords()).isEmpty();
	}

	private AiEvaluationCommand command(String text) {
		return new AiEvaluationCommand(
			42,
			text,
			LocalDate.of(2026, 7, 30),
			List.of(
				new DailyWordCard(101, "햇빛", "명사", "해에서 나오는 빛"),
				new DailyWordCard(102, "골목", "명사", "큰길에서 갈라진 좁은 길"),
				new DailyWordCard(103, "번지다", "동사", "기운이 퍼지다"),
				new DailyWordCard(104, "고요", "명사", "조용하고 잠잠한 상태"),
				new DailyWordCard(105, "온기", "명사", "따뜻한 기운")
			)
		);
	}

	private static GenericContainer<?> createAiServiceContainer() {
		String imageName = System.getenv("AI_CONTRACT_TEST_IMAGE");
		if (imageName == null || imageName.isBlank()) {
			throw new IllegalStateException(
				"AI_CONTRACT_TEST_IMAGE must reference an image built from ai-service/Dockerfile.test"
			);
		}
		return new GenericContainer<>(DockerImageName.parse(imageName))
			.withExposedPorts(AI_SERVICE_PORT)
			.withCommand(
				"python", "-m", "uvicorn", "tests.contract_server:application",
				"--host", "0.0.0.0", "--port", Integer.toString(AI_SERVICE_PORT)
			)
			.waitingFor(Wait.forHttp("/health").forStatusCode(200))
			.withStartupTimeout(Duration.ofMinutes(3));
	}
}
