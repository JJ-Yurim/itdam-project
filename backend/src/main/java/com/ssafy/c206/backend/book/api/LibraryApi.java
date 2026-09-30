package com.ssafy.c206.backend.book.api;

import com.ssafy.c206.backend.common.api.PageResponse;
import com.ssafy.c206.backend.common.security.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

@Tag(
		name = "공동 도서관",
		description = "생성이 완료된 공동 책만 페이지와 상세로 조회합니다.")
public interface LibraryApi {

	@Operation(
			summary = "완성된 책 목록 조회",
			description = "공동 도서관에서 읽을 수 있는 완성본을 찾도록 COMPLETED 책만 페이지 조회합니다. "
					+ "인증 없이도 호출할 수 있는 열람 공개 API입니다. GET /api/v1/library/books에 page와 size 쿼리를 보냅니다. "
					+ "생성 중이거나 실패한 책은 반환하지 않고 page는 0부터 시작하며 size 기본값은 20, 최대값은 100입니다. "
					+ "성공하면 200과 PageResponse<LibraryBookSummaryResponse>를 받습니다. "
					+ "400이면 페이지 값을 고치고, 403이면 계정을 활성화한 뒤 재시도하세요.")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "완성된 책 페이지"),
			@ApiResponse(responseCode = "400", ref = "#/components/responses/BadRequest"),
			@ApiResponse(responseCode = "403", ref = "#/components/responses/Forbidden")
	})
	PageResponse<LibraryBookSummaryResponse> list(
			@Parameter(hidden = true) AuthenticatedUser principal,
			@Parameter(description = "0부터 시작하는 페이지 번호입니다.", example = "0") @Min(0) int page,
			@Parameter(description = "페이지 크기입니다. 기본값은 20이고 최대 100입니다.", example = "20") @Min(1) @Max(100) int size);

	@Operation(
			summary = "완성된 책 상세 조회",
			description = "완성된 책의 수록 문장과 기여자를 읽도록 상세를 조회합니다. "
					+ "인증 없이도 호출할 수 있는 열람 공개 API입니다. GET /api/v1/library/books/{bookId}의 경로 변수로 "
					+ "완성된 책 식별자를 보냅니다. 성공하면 200과 LibraryBookDetailResponse를 받습니다. "
					+ "세션 없이 호출하면 내부 식별자(기여자 userId, sourceSubmissionId)가 null로 내려가고 "
					+ "닉네임과 contentType으로 출처를 표기합니다. "
					+ "403이면 계정을 활성화한 뒤 재시도하고, 404이면 목록에서 공개된 COMPLETED bookId를 다시 선택하세요.")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "완성된 책 상세"),
			@ApiResponse(responseCode = "403", ref = "#/components/responses/Forbidden"),
			@ApiResponse(responseCode = "404", ref = "#/components/responses/NotFound")
	})
	LibraryBookDetailResponse detail(
			@Parameter(hidden = true) AuthenticatedUser principal,
			@Parameter(description = "완성된 책 식별자입니다.", example = "18") Long bookId);
}
