package com.ssafy.c206.backend.boss.image.api;

import com.ssafy.c206.backend.common.security.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.multipart.MultipartFile;

@Tag(name = "관리자 - 책 표지 이미지")
public interface BookCoverImageAdminApi {
	@Operation(summary = "책 표지 이미지 업로드")
	@SecurityRequirement(name = "sessionAuth")
	@SecurityRequirement(name = "csrfToken")
	@ApiResponses({
			@ApiResponse(responseCode = "201", description = "임시 이미지 자산 생성 완료"),
			@ApiResponse(responseCode = "400", ref = "#/components/responses/BadRequest"),
			@ApiResponse(responseCode = "401", ref = "#/components/responses/Unauthorized"),
			@ApiResponse(responseCode = "403", ref = "#/components/responses/Forbidden")
	})
	BossImageUploadResponse upload(
			@Parameter(hidden = true) AuthenticatedUser principal,
			@Parameter(description = "5MB 이하 PNG, JPEG 또는 WebP 파일", required = true) MultipartFile image);
}
