package com.ssafy.c206.backend.book.reaction.api;

import com.ssafy.c206.backend.book.api.LibraryBookSummaryResponse;
import com.ssafy.c206.backend.common.api.PageResponse;
import com.ssafy.c206.backend.common.security.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

@Tag(name = "내 북마크", description = "로그인 사용자가 북마크한 공동 책을 조회합니다.")
public interface MyBookmarkApi {

	@Operation(
			summary = "내 북마크 책 목록 조회",
			description = "현재 사용자가 북마크한 책을 북마크 등록 시각 역순으로 페이지 조회합니다.")
	@SecurityRequirement(name = "sessionAuth")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "내 북마크 책 페이지"),
			@ApiResponse(responseCode = "400", ref = "#/components/responses/BadRequest"),
			@ApiResponse(responseCode = "401", ref = "#/components/responses/Unauthorized"),
			@ApiResponse(responseCode = "403", ref = "#/components/responses/Forbidden")
	})
	PageResponse<LibraryBookSummaryResponse> getMyBookmarks(
			@Parameter(hidden = true) AuthenticatedUser principal,
			@Parameter(description = "0부터 시작하는 페이지 번호입니다.", example = "0") @Min(0) int page,
			@Parameter(description = "페이지 크기입니다. 기본값은 20이고 최대 100입니다.", example = "20") @Min(1) @Max(100) int size);
}
