package com.ssafy.c206.backend.book.api;

import java.util.List;

import com.ssafy.c206.backend.book.domain.BookStatus;
import com.ssafy.c206.backend.book.domain.BookVisibility;
import com.ssafy.c206.backend.common.api.PageResponse;
import com.ssafy.c206.backend.common.security.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.security.core.annotation.AuthenticationPrincipal;

@Tag(name = "관리자 - 책 관리")
public interface BookAdminApi {
	@Operation(
			summary = "관리자 책 목록 조회",
			description = "생성 중·완료·실패 책을 보스 이름 검색어, 생성 상태, 공개 상태로 필터링해 조회합니다.")
	@SecurityRequirement(name = "sessionAuth")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "책 페이지"),
			@ApiResponse(responseCode = "400", ref = "#/components/responses/BadRequest"),
			@ApiResponse(responseCode = "401", ref = "#/components/responses/Unauthorized"),
			@ApiResponse(responseCode = "403", ref = "#/components/responses/Forbidden")
	})
	PageResponse<AdminBookResponse> getBooks(
			@Parameter(description = "보스 이름 검색어입니다.") String query,
			@Parameter(description = "생성 상태 필터입니다. 생략하면 전체입니다.") List<BookStatus> statuses,
			@Parameter(description = "공개 상태 필터입니다. 생략하면 전체입니다.") BookVisibility visibility,
			@Parameter(description = "0부터 시작하는 페이지 번호입니다.", example = "0") @Min(0) int page,
			@Parameter(description = "페이지 크기입니다. 기본값은 20이고 최대 100입니다.", example = "20") @Min(1) @Max(100) int size);

	@Operation(
			summary = "책 공개·비공개 전환",
			description = "완료된 책만 PUBLIC으로 전환할 수 있습니다. HIDDEN 책은 도서관·상세·피드·기여 목록 등 "
					+ "모든 공개 표면에서 제외되고 데이터는 보존됩니다.")
	@SecurityRequirement(name = "sessionAuth")
	@SecurityRequirement(name = "csrfToken")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "공개 상태 전환 완료"),
			@ApiResponse(responseCode = "400", ref = "#/components/responses/BadRequest"),
			@ApiResponse(responseCode = "401", ref = "#/components/responses/Unauthorized"),
			@ApiResponse(responseCode = "403", ref = "#/components/responses/Forbidden"),
			@ApiResponse(responseCode = "404", ref = "#/components/responses/NotFound"),
			@ApiResponse(responseCode = "409", ref = "#/components/responses/Conflict")
	})
	AdminBookResponse updateVisibility(
			@Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser principal,
			@Parameter(description = "책 ID") Long bookId,
			@Valid AdminBookVisibilityUpdateRequest request);

	@Operation(summary = "개별 책 표지 교체")
	@SecurityRequirement(name = "sessionAuth")
	@SecurityRequirement(name = "csrfToken")
	AdminBookCoverResponse changeCover(
			@Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser principal,
			@Parameter(description = "책 ID") Long bookId,
			@Valid AdminBookCoverUpdateRequest request);
}
