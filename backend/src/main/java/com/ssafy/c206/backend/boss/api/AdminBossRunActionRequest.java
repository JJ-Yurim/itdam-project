package com.ssafy.c206.backend.boss.api;

import java.time.Instant;

import jakarta.validation.constraints.NotNull;

public record AdminBossRunActionRequest(@NotNull Instant expectedUpdatedAt) {
}
