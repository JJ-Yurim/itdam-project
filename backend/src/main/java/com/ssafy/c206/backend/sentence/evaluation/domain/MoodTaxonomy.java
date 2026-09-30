package com.ssafy.c206.backend.sentence.evaluation.domain;

import java.util.Map;
import java.util.Set;

/**
 * MVP 감정 분류 10종의 단일 출처.
 * 시딩(MoodCategorySeeder)과 외부 응답 검증(AiEvaluationResultValidator)이 같은 목록을 참조한다.
 */
public final class MoodTaxonomy {

	public static final Map<String, String> CODES_TO_KOREAN_NAMES = Map.ofEntries(
			Map.entry("JOY", "기쁨"), Map.entry("HOPE", "희망"), Map.entry("LOVE", "사랑"),
			Map.entry("CALM", "평온"), Map.entry("NOSTALGIA", "향수"), Map.entry("SADNESS", "슬픔"),
			Map.entry("LONELINESS", "외로움"), Map.entry("ANGER", "분노"), Map.entry("TENSION", "긴장"),
			Map.entry("MYSTERY", "신비"));

	private MoodTaxonomy() {
	}

	public static Set<String> codes() {
		return CODES_TO_KOREAN_NAMES.keySet();
	}
}
