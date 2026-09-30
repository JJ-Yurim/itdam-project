package com.ssafy.c206.backend.book.api;

import java.time.Instant;

import com.ssafy.c206.backend.book.domain.BookVisibility;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

public record AdminBookVisibilityUpdateRequest(
		@Schema(description = "전환할 공개 상태입니다. PUBLIC은 완료된 책만 허용됩니다.", example = "HIDDEN")
		@NotNull BookVisibility visibility,
		@NotNull Instant expectedUpdatedAt) {
}
