package com.ssafy.c206.backend.common.api;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.data.domain.Page;

public record PageResponse<T>(
		@Schema(description = "현재 페이지의 항목입니다.", example = "[]") List<T> content,
		@Schema(description = "현재 페이지 번호입니다. 0부터 시작합니다.", minimum = "0", example = "0") int page,
		@Schema(description = "현재 페이지 크기입니다.", minimum = "1", maximum = "100", example = "20") int size,
		@Schema(description = "조회 조건에 맞는 전체 항목 수입니다.", minimum = "0", example = "57") long totalElements,
		@Schema(description = "전체 페이지 수입니다.", minimum = "0", example = "3") int totalPages,
		@Schema(description = "다음 페이지 존재 여부입니다.", example = "true") boolean hasNext) {

	public static <T> PageResponse<T> from(Page<T> result) {
		return new PageResponse<>(result.getContent(), result.getNumber(), result.getSize(),
				result.getTotalElements(), result.getTotalPages(), result.hasNext());
	}
}
