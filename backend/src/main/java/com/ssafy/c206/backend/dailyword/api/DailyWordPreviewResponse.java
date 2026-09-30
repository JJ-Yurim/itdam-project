package com.ssafy.c206.backend.dailyword.api;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.IntStream;

import com.ssafy.c206.backend.word.domain.Word;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 비회원용 오늘의 단어 미리보기 응답.
 *
 * <p>회원의 개인 세트({@code daily_word_sets})와 달리 지급 이력이 없으므로
 * dailyWordSetId를 포함하지 않는다. 문장 작성에는 쓸 수 없는 열람 전용 데이터다.
 */
public record DailyWordPreviewResponse(
		@Schema(description = "서버가 계산한 서비스 날짜입니다.", format = "date", example = "2026-08-03") LocalDate serviceDate,
		@Schema(description = "미리보기 단어 목록입니다. 같은 서비스 날짜에는 항상 같은 단어가 제공됩니다.")
		List<DailyWordResponse.Item> items) {

	public static DailyWordPreviewResponse of(LocalDate serviceDate, List<Word> words) {
		List<DailyWordResponse.Item> items = IntStream.range(0, words.size())
				.mapToObj(index -> {
					Word word = words.get(index);
					return new DailyWordResponse.Item(index + 1, word.getId(), word.getWord(),
							word.getPartOfSpeech(), word.getDefinition(), word.getExample());
				})
				.toList();
		return new DailyWordPreviewResponse(serviceDate, items);
	}
}
