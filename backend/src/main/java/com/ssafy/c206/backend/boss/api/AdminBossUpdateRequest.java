package com.ssafy.c206.backend.boss.api;

import java.time.Instant;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record AdminBossUpdateRequest(
		@NotBlank @Size(max = 150) String name,
		@NotBlank String description,
		@NotEmpty @Valid List<AdminBossCreateRequest.MoodEntry> moods,
		@NotNull Instant expectedUpdatedAt) {
}
