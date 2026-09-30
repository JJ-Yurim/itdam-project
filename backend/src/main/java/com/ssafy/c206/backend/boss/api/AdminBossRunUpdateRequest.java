package com.ssafy.c206.backend.boss.api;

import java.time.Instant;
import java.util.List;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

public record AdminBossRunUpdateRequest(
		Long bossId,
		Instant startsAt,
		Instant endsAt,
		@ArraySchema(arraySchema = @Schema(description = "교체할 전체 페이즈 목록"), minItems = 1)
		List<AdminBossRunCreateRequest.PhaseEntry> phases,
		@NotNull Instant expectedUpdatedAt) {
}
