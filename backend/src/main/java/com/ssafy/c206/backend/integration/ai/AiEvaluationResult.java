package com.ssafy.c206.backend.integration.ai;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Raw result returned by an AI evaluation provider before backend persistence.
 *
 * <p>AI Service가 계약에 선택적 필드를 덧붙이는 것은 같은 계약 버전 안에서 허용되는 변경이므로
 * (예: 유사도 검사용 embedding), 모르는 필드 때문에 평가 응답 파싱이 실패해서는 안 된다.
 * Jackson 기본값은 모르는 필드를 오류로 처리하므로 이 계층에서만 명시적으로 무시한다.
 * 애플리케이션 공용 ObjectMapper는 자체 API 요청 검증을 위해 기본 동작을 유지한다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AiEvaluationResult(
	SafetyResult safety,
	QualityScores scores,
	String overallFeedback,
	List<MoodResult> moods,
	List<UsedDailyWordResult> usedDailyWords,
	String modelName,
	String promptVersion,
	String rubricVersion,
	// 보스 제출 유사도 검사에 쓸 벡터다. AI Service가 임베딩을 만들지 못하면 null이며,
	// 그 경우에도 평가 자체는 성공으로 처리한다(정책상 임베딩은 부가 결과).
	List<Double> embedding,
	String embeddingModel,
	Integer embeddingDimension
) {
	public AiEvaluationResult {
		// moods·usedDailyWords는 평가의 본체다. 원소가 null이면 응답 자체가 잘못된 것이므로
		// List.copyOf의 NPE로 파싱을 실패시키는 것이 맞다.
		moods = moods == null ? null : List.copyOf(moods);
		usedDailyWords = usedDailyWords == null ? null : List.copyOf(usedDailyWords);
		// 임베딩은 부가 결과다. 벡터에 null 원소가 섞였다고 평가까지 실패시키면 안 되므로
		// null을 허용해 복사하고, 저장 가능 여부는 hasEmbedding()에서 판정한다.
		embedding = embedding == null ? null : Collections.unmodifiableList(new ArrayList<>(embedding));
	}

	/**
	 * 저장 가능한 임베딩이 함께 왔는지 여부.
	 *
	 * <p>메타데이터가 빠졌거나 선언한 차원과 실제 벡터 길이가 다르면 저장하지 않는다.
	 * 외부 응답을 신뢰하는 경계이므로, 불일치를 여기서 걸러내지 않으면 저장 시점의 SQL 오류가
	 * 이미 성공한 평가 트랜잭션을 롤백시킨다.
	 */
	public boolean hasEmbedding() {
		return embedding != null && !embedding.isEmpty()
			&& embedding.stream().noneMatch(Objects::isNull)
			&& embeddingModel != null && !embeddingModel.isBlank()
			&& embeddingDimension != null
			&& embeddingDimension == embedding.size();
	}
}
