package com.ssafy.c206.backend.sentence.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import io.swagger.v3.oas.annotations.media.Schema;

public record SentenceRequest(
		@Schema(description = "작성하거나 수정할 문장 내용입니다. 공백만으로 구성될 수 없고 최대 500자입니다.", example = "비가 그친 골목에 햇빛이 천천히 번졌다.")
		@NotBlank(message = "content must not be blank")
		@Size(max = 500, message = "content must be at most 500 characters")
		String content) {
}
