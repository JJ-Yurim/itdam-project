package com.ssafy.c206.backend.boss.api;

import java.math.BigDecimal;

import com.ssafy.c206.backend.sentence.evaluation.domain.SentenceEvaluation;
import io.swagger.v3.oas.annotations.media.Schema;

public record EligibleSentenceResponse(
		@Schema(description = "제출 가능한 문장 버전 식별자입니다.", example = "84") Long sentenceVersionId,
		@Schema(description = "문장 식별자입니다.", example = "42") Long sentenceId,
		@Schema(description = "문장 버전 번호입니다.", minimum = "1", example = "2") int versionNo,
		@Schema(description = "제출 가능한 문장 내용입니다.", example = "햇빛이 창가에 부드럽게 번졌다.") String content,
		@Schema(description = "보너스 전 기본 평가 점수입니다.", example = "39.00") BigDecimal baseScore,
		@Schema(description = "오늘의 단어 사용 보너스입니다.", example = "1.50") BigDecimal wordBonus,
		@Schema(description = "분위기·약점 보너스 전 공격력입니다.", example = "12.50") BigDecimal gameBaseAttack,
		@Schema(description = "현재 보스의 분위기·약점 보너스와 페이즈 배율을 반영한 예상 문장 에너지입니다.", example = "57") long sentenceEnergy) {
	public static EligibleSentenceResponse from(SentenceEvaluation evaluation, long sentenceEnergy) {
		var version = evaluation.getSentenceVersion();
		return new EligibleSentenceResponse(version.getId(), version.getSentence().getId(), version.getVersionNo(),
				version.getContent(), evaluation.getBaseScore(), evaluation.getWordBonus(), evaluation.getGameBaseAttack(),
				sentenceEnergy);
	}
}
