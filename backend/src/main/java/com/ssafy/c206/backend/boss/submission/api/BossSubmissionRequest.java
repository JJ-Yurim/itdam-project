package com.ssafy.c206.backend.boss.submission.api;

import jakarta.validation.constraints.NotNull;
import io.swagger.v3.oas.annotations.media.Schema;

public record BossSubmissionRequest(
		@Schema(description = "제출할 평가 완료 문장 버전 식별자입니다.", example = "84") @NotNull Long sentenceVersionId,
		@Schema(description = "문장을 보스전에 제출하고 피해 계산에 사용하는 것에 동의했는지 여부입니다. 반드시 true여야 합니다.", example = "true") boolean submissionConsentAccepted) { }
