package com.ssafy.c206.backend.book.feed.api;

import com.ssafy.c206.backend.common.security.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "주간 선정 문장 피드", description = "지난주 완성된 공동 책에서 무작위 선정한 문장을 주 단위로 제공합니다.")
public interface WeeklySentenceFeedApi {

	@Operation(
			summary = "이번 주 선정 문장 피드 조회",
			description = "지난주(월~일)에 완성된 공동 책의 사용자 작성 문장 중 무작위로 선정한 세트를 반환합니다. "
					+ "세트는 주 시작 후 첫 조회 시점에 발급되며, 같은 주에는 누가 조회해도 동일한 문장이 반환됩니다.")
	@SecurityRequirement(name = "sessionAuth")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "이번 주 선정 문장 세트"),
			@ApiResponse(responseCode = "401", ref = "#/components/responses/Unauthorized"),
			@ApiResponse(responseCode = "403", ref = "#/components/responses/Forbidden")
	})
	WeeklySentenceFeedResponse getFeed(@Parameter(hidden = true) AuthenticatedUser principal);
}
