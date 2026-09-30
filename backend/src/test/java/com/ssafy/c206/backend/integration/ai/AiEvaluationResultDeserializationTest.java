package com.ssafy.c206.backend.integration.ai;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * AI Service가 같은 계약 버전 안에서 선택적 필드를 덧붙여도 평가 응답 파싱이 깨지지 않는지 검증한다.
 * Jackson 기본값은 모르는 필드를 오류로 처리하므로, 이 보장이 없으면 AI Service 배포만으로
 * Backend의 모든 평가가 실패한다.
 */
@SpringBootTest
@ActiveProfiles("test")
class AiEvaluationResultDeserializationTest {

	@Autowired
	private ObjectMapper objectMapper;

	@Test
	void unknownOptionalFieldsDoNotBreakEvaluationParsing() throws Exception {
		String payload = """
				{
				  "safety": {"status": "PASSED", "reason": null, "futureSafetyField": "ignored"},
				  "scores": {"completeness": 80, "originality": 76, "expression": 82,
				             "imagery": 74, "emotionalImpact": 79, "futureScore": 12},
				  "overallFeedback": "feedback",
				  "moods": [{"categoryCode": "CALM", "strength": 0.5, "rank": 1, "futureMoodField": true}],
				  "usedDailyWords": [{"wordId": 1, "used": true, "futureUsageField": "x"}],
				  "modelName": "model",
				  "promptVersion": "prompt-v1",
				  "rubricVersion": "rubric-v1",
				  "embedding": [0.1, 0.2],
				  "embeddingModel": "text-embedding-3-small",
				  "embeddingDimension": 768
				}
				""";

		AiEvaluationResult result = objectMapper.readValue(payload, AiEvaluationResult.class);

		assertThat(result.safety().status()).isEqualTo(SafetyStatus.PASSED);
		assertThat(result.scores().completeness()).isEqualTo(80);
		assertThat(result.moods()).hasSize(1);
		assertThat(result.usedDailyWords()).hasSize(1);
		assertThat(result.modelName()).isEqualTo("model");
	}

	@Test
	void embeddingFieldsAreMappedFromTheHttpResponse() throws Exception {
		// HTTP 어댑터는 중간 DTO 없이 이 record로 직접 역직렬화한다.
		// 이 매핑이 끊기면 운영에서 벡터가 한 건도 저장되지 않고 정책이 조용히 꺼진다.
		String payload = """
				{
				  "safety": {"status": "PASSED", "reason": null},
				  "scores": {"completeness": 80, "originality": 76, "expression": 82,
				             "imagery": 74, "emotionalImpact": 79},
				  "overallFeedback": "feedback",
				  "moods": [],
				  "usedDailyWords": [],
				  "modelName": "model",
				  "promptVersion": "prompt-v1",
				  "rubricVersion": "rubric-v1",
				  "embedding": [0.1, 0.2, 0.3],
				  "embeddingModel": "text-embedding-3-small",
				  "embeddingDimension": 3
				}
				""";

		AiEvaluationResult result = objectMapper.readValue(payload, AiEvaluationResult.class);

		assertThat(result.embedding()).containsExactly(0.1, 0.2, 0.3);
		assertThat(result.embeddingModel()).isEqualTo("text-embedding-3-small");
		assertThat(result.embeddingDimension()).isEqualTo(3);
		assertThat(result.hasEmbedding()).isTrue();
	}

	@Test
	void nullElementsInTheEmbeddingDoNotBreakEvaluationParsing() throws Exception {
		// 임베딩은 부가 결과다. 벡터가 깨졌다고 평가 응답 전체가 실패하면 안 된다.
		String payload = """
				{
				  "safety": {"status": "PASSED", "reason": null},
				  "scores": {"completeness": 80, "originality": 76, "expression": 82,
				             "imagery": 74, "emotionalImpact": 79},
				  "overallFeedback": "feedback",
				  "moods": [],
				  "usedDailyWords": [],
				  "modelName": "model",
				  "promptVersion": "prompt-v1",
				  "rubricVersion": "rubric-v1",
				  "embedding": [0.1, null, 0.3],
				  "embeddingModel": "text-embedding-3-small",
				  "embeddingDimension": 3
				}
				""";

		AiEvaluationResult result = objectMapper.readValue(payload, AiEvaluationResult.class);

		assertThat(result.safety().status()).isEqualTo(SafetyStatus.PASSED);
		// 파싱은 성공하되 저장 대상에서는 제외된다.
		assertThat(result.hasEmbedding()).isFalse();
	}

	@Test
	void applicationRequestParsingStillRejectsUnknownFields() throws Exception {
		// 자체 API 요청 검증까지 느슨해지면 안 되므로, 공용 ObjectMapper의 기본 동작은 유지한다.
		assertThat(objectMapper.getDeserializationConfig()
				.isEnabled(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES))
				.isTrue();
	}
}
