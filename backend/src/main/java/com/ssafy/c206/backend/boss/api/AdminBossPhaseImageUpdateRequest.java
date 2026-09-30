package com.ssafy.c206.backend.boss.api;

import java.time.Instant;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

public record AdminBossPhaseImageUpdateRequest(
		@Schema(description = "관리자 본인이 업로드한 TEMPORARY 상태의 BOSS_PHASE 이미지 자산 ID", example = "321")
		@NotNull Long imageAssetId,
		@NotNull Instant expectedUpdatedAt) {
}
