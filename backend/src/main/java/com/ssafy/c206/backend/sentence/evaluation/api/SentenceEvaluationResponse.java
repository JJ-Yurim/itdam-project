package com.ssafy.c206.backend.sentence.evaluation.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import com.ssafy.c206.backend.sentence.domain.EvaluationStatus;
import com.ssafy.c206.backend.sentence.evaluation.domain.SentenceEvaluation;
import com.ssafy.c206.backend.sentence.evaluation.domain.SentenceMood;
import com.ssafy.c206.backend.sentence.evaluation.domain.SentenceUsedDailyWord;
import io.swagger.v3.oas.annotations.media.Schema;

public record SentenceEvaluationResponse(
		@Schema(description = "평가한 문장 버전 식별자입니다.", example = "84") Long sentenceVersionId,
		@Schema(description = "평가 상태입니다. DRAFT는 초안, EVALUATING은 진행 중, EVALUATED는 완료, REJECTED는 안전성 거부, EVALUATION_FAILED는 기술 실패입니다.", allowableValues = {"DRAFT", "EVALUATING", "EVALUATED", "REJECTED", "EVALUATION_FAILED"}, example = "EVALUATED") String evaluationStatus,
		@Schema(description = "안전성 검사 결과입니다. 아직 평가하지 않은 상태에서는 null입니다.", example = "{\"status\":\"PASSED\",\"reason\":null}", nullable = true) Safety safety,
		@Schema(description = "세부 평가 점수입니다. 아직 평가하지 않은 상태에서는 null입니다.", example = "{\"completeness\":8,\"originality\":7,\"expression\":9,\"imagery\":8,\"emotionalImpact\":7,\"baseScore\":39.00}", nullable = true) Scores scores,
		@Schema(description = "오늘의 단어 사용으로 얻은 보너스 점수입니다.", example = "1.50", nullable = true) BigDecimal wordBonus,
		@Schema(description = "게임에 반영되는 기본 공격력입니다.", example = "12.50", nullable = true) BigDecimal gameBaseAttack,
		@Schema(description = "문장에 대한 종합 피드백입니다.", example = "이미지가 선명하고 문장 흐름이 자연스럽습니다.", nullable = true) String overallFeedback,
		@Schema(description = "문장에서 감지된 분위기 목록입니다.", example = "[{\"code\":\"CALM\",\"strength\":0.82,\"rank\":1}]") List<Mood> moods,
		@Schema(description = "문장에서 사용한 오늘의 단어 목록입니다.", example = "[{\"dailyWordSetItemId\":15,\"wordId\":9,\"word\":\"햇빛\"}]") List<UsedDailyWord> usedDailyWords,
		@Schema(description = "평가에 사용한 모델 이름입니다.", example = "gpt-4.1-mini", nullable = true) String modelName,
		@Schema(description = "평가 프롬프트 버전입니다.", example = "2026-07-01", nullable = true) String promptVersion,
		@Schema(description = "평가 루브릭 버전입니다.", example = "v1", nullable = true) String rubricVersion,
		@Schema(description = "평가를 완료한 시각입니다. 미완료면 null이며 ISO-8601 UTC date-time 형식입니다.", format = "date-time", example = "2026-07-27T02:23:45Z", nullable = true) Instant evaluatedAt) {

	public static SentenceEvaluationResponse pending(Long versionId, EvaluationStatus status) {
		Safety safety = status == EvaluationStatus.REJECTED ? new Safety("REJECTED", null) : null;
		return new SentenceEvaluationResponse(versionId, status.name(), safety, null, null, null, null,
				List.of(), List.of(), null, null, null, null);
	}

	public static SentenceEvaluationResponse from(SentenceEvaluation evaluation, List<SentenceMood> moods,
			List<SentenceUsedDailyWord> usedDailyWords) {
		return new SentenceEvaluationResponse(evaluation.getSentenceVersion().getId(), "EVALUATED",
				new Safety(evaluation.getSafetyStatus().name(), evaluation.getSafetyReason()),
				new Scores(evaluation.getCompletenessScore(), evaluation.getOriginalityScore(), evaluation.getExpressionScore(),
						evaluation.getImageryScore(), evaluation.getEmotionalImpactScore(), evaluation.getBaseScore()),
				evaluation.getWordBonus(), evaluation.getGameBaseAttack(), evaluation.getOverallFeedback(),
				moods.stream().map(mood -> new Mood(mood.getMoodCategory().getCode(), mood.getStrength(), mood.getRank())).toList(),
				usedDailyWords.stream().map(used -> new UsedDailyWord(used.getDailyWordSetItem().getId(),
						used.getDailyWordSetItem().getWord().getId(), used.getDailyWordSetItem().getWord().getWord())).toList(),
				evaluation.getModelName(), evaluation.getPromptVersion(), evaluation.getRubricVersion(), evaluation.getEvaluatedAt());
	}

	public record Safety(
			@Schema(description = "안전성 상태입니다. PASSED는 통과, REJECTED는 거부입니다.", allowableValues = {"PASSED", "REJECTED"}, example = "PASSED") String status,
			@Schema(description = "안전성 거부 사유입니다. 통과하면 null입니다.", example = "유해 표현이 감지되었습니다.", nullable = true) String reason) { }
	public record Scores(
			@Schema(description = "완결성 점수입니다. 0~100 범위입니다.", minimum = "0", maximum = "100", example = "8") Integer completeness,
			@Schema(description = "독창성 점수입니다. 0~100 범위입니다.", minimum = "0", maximum = "100", example = "7") Integer originality,
			@Schema(description = "표현력 점수입니다. 0~100 범위입니다.", minimum = "0", maximum = "100", example = "9") Integer expression,
			@Schema(description = "심상 점수입니다. 0~100 범위입니다.", minimum = "0", maximum = "100", example = "8") Integer imagery,
			@Schema(description = "정서적 영향 점수입니다. 0~100 범위입니다.", minimum = "0", maximum = "100", example = "7") Integer emotionalImpact,
			@Schema(description = "보너스 전 기본 점수입니다.", example = "39.00") BigDecimal baseScore) { }
	public record Mood(
			@Schema(description = "분위기 분류 코드입니다.", allowableValues = {"JOY", "HOPE", "LOVE", "CALM", "NOSTALGIA", "SADNESS", "LONELINESS", "ANGER", "TENSION", "MYSTERY"}, example = "CALM") String code,
			@Schema(description = "분위기 강도입니다. 0~1 범위입니다.", minimum = "0", maximum = "1", example = "0.82") BigDecimal strength,
			@Schema(description = "강도 순위입니다. 1이 가장 높습니다.", minimum = "1", example = "1") int rank) { }
	public record UsedDailyWord(
			@Schema(description = "오늘의 단어 세트 항목 식별자입니다.", example = "15") Long dailyWordSetItemId,
			@Schema(description = "사용한 단어 식별자입니다.", example = "9") Long wordId,
			@Schema(description = "사용한 오늘의 단어입니다.", example = "햇빛") String word) { }
}
