package com.ssafy.c206.backend.common.api;

import io.swagger.v3.oas.annotations.media.Schema;

public record ApiRootResponse(
		@Schema(description = "API 이름입니다.", example = "mvp-backend") String name,
		@Schema(description = "API 버전입니다.", example = "v1") String version) {
}
