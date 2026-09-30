package com.ssafy.c206.backend.book.feed.api;

import com.ssafy.c206.backend.common.security.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "문장 좋아요", description = "완성 공동 책에 수록된 문장(책 콘텐츠)에 대한 좋아요를 관리합니다.")
public interface BookContentLikeApi {

	@Operation(
			summary = "문장 좋아요 등록",
			description = "완성(COMPLETED)된 공동 책의 문장에 좋아요를 등록합니다. 이미 등록된 상태여도 성공(204)으로 처리되는 멱등 API입니다.")
	@SecurityRequirement(name = "sessionAuth")
	@ApiResponses({
			@ApiResponse(responseCode = "204", description = "좋아요 등록 완료(이미 등록된 경우 포함)"),
			@ApiResponse(responseCode = "401", ref = "#/components/responses/Unauthorized"),
			@ApiResponse(responseCode = "403", ref = "#/components/responses/Forbidden"),
			@ApiResponse(responseCode = "404", ref = "#/components/responses/NotFound")
	})
	void like(@Parameter(hidden = true) AuthenticatedUser principal,
			@Parameter(description = "좋아요 대상 책 콘텐츠 식별자입니다.", example = "42") Long bookContentId);

	@Operation(
			summary = "문장 좋아요 해제",
			description = "문장에 등록한 좋아요를 해제합니다. 좋아요가 없는 상태여도 성공(204)으로 처리되는 멱등 API입니다.")
	@SecurityRequirement(name = "sessionAuth")
	@ApiResponses({
			@ApiResponse(responseCode = "204", description = "좋아요 해제 완료(좋아요가 없던 경우 포함)"),
			@ApiResponse(responseCode = "401", ref = "#/components/responses/Unauthorized"),
			@ApiResponse(responseCode = "403", ref = "#/components/responses/Forbidden"),
			@ApiResponse(responseCode = "404", ref = "#/components/responses/NotFound")
	})
	void unlike(@Parameter(hidden = true) AuthenticatedUser principal,
			@Parameter(description = "좋아요 해제 대상 책 콘텐츠 식별자입니다.", example = "42") Long bookContentId);
}
