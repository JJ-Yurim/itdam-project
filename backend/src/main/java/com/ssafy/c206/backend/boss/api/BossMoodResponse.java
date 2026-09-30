package com.ssafy.c206.backend.boss.api;

import java.math.BigDecimal;

import com.ssafy.c206.backend.boss.domain.BossMood;
import com.ssafy.c206.backend.boss.domain.MoodRole;
import io.swagger.v3.oas.annotations.media.Schema;

public record BossMoodResponse(
		@Schema(description = "분위기 분류 코드입니다.", example = "CALM") String code,
		@Schema(description = "표시할 분위기 이름입니다.", example = "평온") String displayName,
		@Schema(description = "분위기의 역할입니다. THEME는 주제, WEAKNESS는 약점입니다.", allowableValues = {"THEME", "WEAKNESS"}, example = "WEAKNESS") MoodRole role,
		@Schema(description = "분위기가 피해 계산에 반영되는 가중치입니다.", example = "1.20") BigDecimal weight) {

	public static BossMoodResponse from(BossMood bossMood) {
		return new BossMoodResponse(bossMood.getMoodCategory().getCode(), bossMood.getMoodCategory().getNameKo(),
				bossMood.getMoodRole(), bossMood.getWeight());
	}
}
