package com.ssafy.c206.backend.sentence.archive.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import com.ssafy.c206.backend.sentence.evaluation.domain.SentenceEvaluation;
import com.ssafy.c206.backend.sentence.evaluation.domain.SentenceMood;
import com.ssafy.c206.backend.sentence.evaluation.domain.SentenceUsedDailyWord;
import com.ssafy.c206.backend.sentence.domain.EvaluationStatus;
import com.ssafy.c206.backend.boss.api.BossSubmissionSummary;
import com.ssafy.c206.backend.book.api.BookInclusionSummary;
import io.swagger.v3.oas.annotations.media.Schema;

public record ArchiveSentenceResponse(
		@Schema(description = "보관한 문장 버전 식별자입니다.", example = "84") Long sentenceVersionId,
		@Schema(description = "문장 식별자입니다.", example = "42") Long sentenceId,
		@Schema(description = "문장 안에서 증가하는 버전 번호입니다.", minimum = "1", example = "2") int versionNo,
		@Schema(description = "보관한 문장 내용입니다.", example = "비가 그친 골목에 햇빛이 천천히 번졌다.") String content,
		@Schema(description = "평가 진행 상태입니다. 이 API에서는 EVALUATED만 반환합니다.", example = "EVALUATED")
		EvaluationStatus evaluationStatus,
		@Schema(description = "문장 버전을 만든 시각입니다.", format = "date-time", example = "2026-07-27T01:23:45Z") Instant createdAt,
		@Schema(description = "보너스 전 기본 평가 점수입니다.", example = "39.00") BigDecimal baseScore,
		@Schema(description = "오늘의 단어 사용으로 얻은 보너스 점수입니다.", example = "1.50") BigDecimal wordBonus,
		@Schema(description = "게임에 반영되는 기본 공격력입니다.", example = "12.50") BigDecimal gameBaseAttack,
		@Schema(description = "문장에서 감지된 분위기 목록입니다.", example = "[{\"code\":\"CALM\",\"strength\":0.82,\"rank\":1}]") List<Mood> moods,
		@Schema(description = "문장에서 사용한 오늘의 단어 목록입니다.", example = "[{\"dailyWordSetItemId\":15,\"wordId\":9,\"word\":\"햇빛\"}]") List<UsedDailyWord> usedDailyWords,
		@Schema(description = "이 문장 버전의 보스 제출 요약 목록입니다.", example = "[]") List<BossSubmissionSummary> bossSubmissions,
		@Schema(description = "이 문장 버전의 책 수록 요약 목록입니다.", example = "[]") List<BookInclusionSummary> bookInclusions,
		@Schema(description = "문장을 평가한 시각입니다. ISO-8601 UTC date-time 형식입니다.", format = "date-time", example = "2026-07-27T02:23:45Z") Instant evaluatedAt) {

	public static ArchiveSentenceResponse from(SentenceEvaluation evaluation, List<SentenceMood> moods,
			List<SentenceUsedDailyWord> usedDailyWords, List<BossSubmissionSummary> bossSubmissions,
			List<BookInclusionSummary> bookInclusions) {
		var version = evaluation.getSentenceVersion();
		return new ArchiveSentenceResponse(version.getId(), version.getSentence().getId(), version.getVersionNo(),
				version.getContent(), version.getEvaluationStatus(), version.getCreatedAt(),
				evaluation.getBaseScore(), evaluation.getWordBonus(), evaluation.getGameBaseAttack(),
				moods.stream().map(mood -> new Mood(mood.getMoodCategory().getCode(), mood.getStrength(), mood.getRank())).toList(),
				usedDailyWords.stream().map(used -> new UsedDailyWord(used.getDailyWordSetItem().getId(),
					used.getDailyWordSetItem().getWord().getId(), used.getDailyWordSetItem().getWord().getWord())).toList(),
				bossSubmissions,
				bookInclusions,
				evaluation.getEvaluatedAt());
	}

	public record Mood(
			@Schema(description = "분위기 분류 코드입니다.", allowableValues = {"JOY", "HOPE", "LOVE", "CALM", "NOSTALGIA", "SADNESS", "LONELINESS", "ANGER", "TENSION", "MYSTERY"}, example = "CALM") String code,
			@Schema(description = "분위기 강도입니다. 0~1 범위입니다.", minimum = "0", maximum = "1", example = "0.82") BigDecimal strength,
			@Schema(description = "강도 순위입니다. 1이 가장 높습니다.", minimum = "1", example = "1") int rank) { }
	public record UsedDailyWord(
			@Schema(description = "오늘의 단어 세트 항목 식별자입니다.", example = "15") Long dailyWordSetItemId,
			@Schema(description = "사용한 단어 식별자입니다.", example = "9") Long wordId,
			@Schema(description = "사용한 오늘의 단어입니다.", example = "햇빛") String word) { }
}
