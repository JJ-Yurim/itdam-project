package com.ssafy.c206.backend.dailyword.api;

import java.time.LocalDate;
import java.util.List;

import com.ssafy.c206.backend.dailyword.domain.DailyWordSet;
import com.ssafy.c206.backend.dailyword.domain.DailyWordSetItem;
import io.swagger.v3.oas.annotations.media.Schema;

public record DailyWordResponse(
		@Schema(description = "오늘의 단어 세트 식별자입니다.", example = "12") Long dailyWordSetId,
		@Schema(description = "서버가 계산한 서비스 날짜입니다.", format = "date", example = "2026-07-27") LocalDate serviceDate,
		@Schema(description = "오늘 제공하는 단어 목록입니다.", example = "[{\"position\":1,\"word\":\"햇빛\"}]") List<Item> items) {

	public static DailyWordResponse from(DailyWordSet set, List<DailyWordSetItem> items) {
		return new DailyWordResponse(set.getId(), set.getServiceDate(), items.stream().map(Item::from).toList());
	}

	public record Item(
			@Schema(description = "세트 안의 노출 순서입니다.", minimum = "1", example = "1") int position,
			@Schema(description = "단어 식별자입니다.", example = "9") Long wordId,
			@Schema(description = "오늘의 단어입니다.", example = "햇빛") String word,
			@Schema(description = "품사입니다.", example = "명사") String partOfSpeech,
			@Schema(description = "단어 뜻입니다.", example = "해에서 나오는 빛") String definition,
			@Schema(description = "단어 사용 예문입니다.", example = "햇빛이 창가에 부드럽게 번졌다.") String example) {

		private static Item from(DailyWordSetItem item) {
			return new Item(item.getPosition(), item.getWord().getId(), item.getWord().getWord(),
					item.getWord().getPartOfSpeech(), item.getWord().getDefinition(), item.getWord().getExample());
		}
	}
}
