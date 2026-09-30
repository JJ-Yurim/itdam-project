package com.ssafy.c206.backend.boss.api;

import java.time.Instant;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

public record AdminBossPhaseTemplatesUpdateRequest(
		@NotEmpty @Valid List<AdminBossCreateRequest.PhaseTemplateEntry> phaseTemplates,
		@NotNull Instant expectedUpdatedAt) {
}
