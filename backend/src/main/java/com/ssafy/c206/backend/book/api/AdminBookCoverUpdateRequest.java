package com.ssafy.c206.backend.book.api;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

public record AdminBookCoverUpdateRequest(
		@Schema(description = "책 표지 이미지 자산 ID", requiredMode = Schema.RequiredMode.REQUIRED)
		@NotNull Long imageAssetId) {
}
