package com.ssafy.c206.backend.book.api;

import java.math.BigDecimal;
import java.util.List;

import com.ssafy.c206.backend.book.domain.BookContent;
import com.ssafy.c206.backend.book.domain.ContentType;
import com.ssafy.c206.backend.sentence.evaluation.domain.SentenceMood;
import com.ssafy.c206.backend.sentence.evaluation.domain.SentenceUsedDailyWord;
import io.swagger.v3.oas.annotations.media.Schema;

public record BookContentResponse(
		@Schema(description = "책 콘텐츠 식별자입니다.", example = "101") Long contentId,
		@Schema(description = "책 안의 수록 순서입니다.", minimum = "1", example = "1") int sequenceNo,
		@Schema(description = "콘텐츠 출처 유형입니다. USER_ORIGINAL은 원문, USER_EDITED는 사용자 편집본, AI_GENERATED는 AI 생성문입니다.", allowableValues = {"USER_ORIGINAL", "USER_EDITED", "AI_GENERATED"}, example = "USER_ORIGINAL") ContentType contentType,
		@Schema(description = "책에 수록된 본문입니다.", example = "햇빛이 창가를 부드럽게 비췄다.") String content,
		@Schema(description = "사용자 기여 콘텐츠면 기여자 정보이고 AI 생성 콘텐츠면 null입니다.", nullable = true) ContributorResponse contributor,
		@Schema(description = "보스 제출에서 비롯된 콘텐츠면 제출 식별자이고, 그렇지 않으면 null입니다. 비회원 열람에서는 null입니다.", nullable = true, example = "55") Long sourceSubmissionId,
		@Schema(description = "보스 제출에서 비롯된 사용자 문장의 제작 정보입니다. AI 생성 콘텐츠면 null입니다.", nullable = true) ContributionDetails contribution) {
	/**
	 * @param exposeInternalIds 비회원 열람이면 false. 내부 식별자(기여자 userId, 제출 ID)를
	 *                          비회원에게 보내지 않기 위해 응답 조립 시 제어한다. 출처 표기는
	 *                          contentType과 닉네임으로 충분하다(ISSUE-116).
	 */
	public static BookContentResponse from(BookContent value, boolean exposeInternalIds, List<SentenceMood> moods,
			List<SentenceUsedDailyWord> usedDailyWords) {
		var submission = value.getSourceSubmission();
		ContributionDetails contribution = submission == null ? null
				: new ContributionDetails(
						value.getContentType() == ContentType.USER_EDITED
								? submission.getSentenceVersion().getContent()
								: null,
						submission.getFinalDamage(),
						moods.stream().map(Mood::from).toList(),
						usedDailyWords.stream()
								.map(used -> used.getDailyWordSetItem().getWord().getWord())
								.toList());
		return new BookContentResponse(value.getId(), value.getSequenceNo(), value.getContentType(), value.getContent(),
				value.getAuthor() == null ? null
						: new ContributorResponse(exposeInternalIds ? value.getAuthor().getId() : null,
								value.getAuthor().getNickname()),
				submission == null || !exposeInternalIds ? null : submission.getId(), contribution);
	}

	public record ContributionDetails(
			@Schema(description = "AI가 다듬기 전 사용자의 제출 원문입니다. 원문 그대로 수록됐으면 null입니다.", nullable = true) String originalContent,
			@Schema(description = "이 문장이 보스에게 준 최종 피해량입니다.", example = "137") long finalDamage,
			@Schema(description = "문장에서 감지된 분위기입니다.") List<Mood> moods,
			@Schema(description = "문장에 사용된 오늘의 단어입니다.", example = "[\"은하수\",\"윤슬\"]") List<String> usedDailyWords) { }

	public record Mood(
			@Schema(description = "분위기 코드입니다.", example = "CALM") String code,
			@Schema(description = "분위기 강도입니다.", minimum = "0", maximum = "1", example = "0.82") BigDecimal strength,
			@Schema(description = "분위기 강도 순위입니다.", minimum = "1", example = "1") int rank) {
		private static Mood from(SentenceMood mood) {
			return new Mood(mood.getMoodCategory().getCode(), mood.getStrength(), mood.getRank());
		}
	}

	public record ContributorResponse(
			@Schema(description = "기여자 사용자 식별자입니다. 비회원 열람에서는 null입니다.", nullable = true,
					example = "42") Long userId,
			@Schema(description = "기여자 표시 별명입니다.", example = "문장여행자") String nickname) { }
}
