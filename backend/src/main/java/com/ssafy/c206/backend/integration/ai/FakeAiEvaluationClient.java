package com.ssafy.c206.backend.integration.ai;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import org.springframework.stereotype.Component;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;

/**
 * Deterministic local/test adapter. It deliberately does not inspect sentence
 * content; scenarios and optional used-word IDs fully determine the result.
 *
 * <p>프로필 가드는 배포 프로필(develop·prod)에서 이 빈이 뜨는 것을 원천 차단한다.
 * matchIfMissing 폴백은 허용된 프로필 안에서만 동작하므로, 배포 프로필이
 * ai.evaluation.client 설정을 빠뜨리면 가짜 평가가 조용히 저장되는 대신
 * AiEvaluationClient 빈 부재로 기동이 실패해 즉시 드러난다.
 */
@Component
@Profile({"local", "test", "loadtest", "postgres-test"})
@ConditionalOnProperty(prefix = "ai.evaluation", name = "client", havingValue = "fake", matchIfMissing = true)
public class FakeAiEvaluationClient implements AiEvaluationClient {

	private static final String MODEL_NAME = "fake-evaluator";
	private static final String VERSION = "fake-v1";

	private final FakeAiEvaluationScenario scenario;
	private final Set<Long> usedWordIds;

	public FakeAiEvaluationClient() {
		this(FakeAiEvaluationScenario.SUCCESS, Set.of());
	}

	public FakeAiEvaluationClient(FakeAiEvaluationScenario scenario) {
		this(scenario, Set.of());
	}

	public FakeAiEvaluationClient(FakeAiEvaluationScenario scenario, Set<Long> usedWordIds) {
		this.scenario = Objects.requireNonNull(scenario, "scenario must not be null.");
		this.usedWordIds = Set.copyOf(Objects.requireNonNull(usedWordIds, "usedWordIds must not be null."));
	}

	@Override
	public AiEvaluationResult evaluate(AiEvaluationCommand command) {
		Objects.requireNonNull(command, "command must not be null.");
		return switch (scenario) {
			case SUCCESS -> success(command);
			case SAFETY_REJECTED -> rejected();
			case TRANSIENT_FAILURE -> throw new AiEvaluationTransientException("Fake AI transient failure.");
			case INVALID_RESPONSE -> throw invalidResponse(command);
		};
	}

	private AiEvaluationResult success(AiEvaluationCommand command) {
		AiEvaluationResult result = new AiEvaluationResult(
			new SafetyResult(SafetyStatus.PASSED, null),
			new QualityScores(80, 76, 82, 74, 79),
			"Fake evaluation feedback.",
			List.of(new MoodResult("NOSTALGIA", new BigDecimal("0.92"), 1)),
			command.dailyWords().stream()
				.map(card -> new UsedDailyWordResult(card.wordId(), usedWordIds.contains(card.wordId())))
				.toList(),
			MODEL_NAME,
			VERSION,
			VERSION,
			// Fake는 임베딩을 만들지 않는다. 유사도 비교는 pgvector 통합 테스트에서 검증한다.
			null,
			null,
			null
		);
		AiEvaluationResultValidator.validate(command, result);
		return result;
	}

	private AiEvaluationResult rejected() {
		return new AiEvaluationResult(
			new SafetyResult(SafetyStatus.REJECTED, "Fake safety policy rejection."),
			null,
			null,
			List.of(),
			List.of(),
			MODEL_NAME,
			VERSION,
			VERSION,
			null,
			null,
			null
		);
	}

	private AiEvaluationInvalidResponseException invalidResponse(AiEvaluationCommand command) {
		AiEvaluationResult invalid = new AiEvaluationResult(
			new SafetyResult(SafetyStatus.PASSED, null),
			new QualityScores(101, 76, 82, 74, 79),
			"Fake invalid response.",
			List.of(),
			command.dailyWords().stream().map(card -> new UsedDailyWordResult(card.wordId(), false)).toList(),
			MODEL_NAME,
			VERSION,
			VERSION,
			null,
			null,
			null
		);
		try {
			AiEvaluationResultValidator.validate(command, invalid);
			throw new IllegalStateException("Invalid fake response unexpectedly passed validation.");
		} catch (AiEvaluationInvalidResponseException exception) {
			return exception;
		}
	}
}
