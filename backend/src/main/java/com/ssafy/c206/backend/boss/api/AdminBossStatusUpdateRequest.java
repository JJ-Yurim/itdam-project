package com.ssafy.c206.backend.boss.api;

import java.time.Instant;

import com.ssafy.c206.backend.boss.domain.BossStatus;
import jakarta.validation.constraints.NotNull;

public record AdminBossStatusUpdateRequest(@NotNull BossStatus status, @NotNull Instant expectedUpdatedAt) {
}
