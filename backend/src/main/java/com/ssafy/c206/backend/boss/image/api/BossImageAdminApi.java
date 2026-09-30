package com.ssafy.c206.backend.boss.image.api;

import com.ssafy.c206.backend.common.security.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.multipart.MultipartFile;

@Tag(name = "관리자 - 보스 이미지", description = "보스 페이즈 이미지를 임시 자산으로 업로드합니다.")
public interface BossImageAdminApi {

	@Operation(summary = "보스 페이즈 이미지 업로드",
			description = "PNG, JPEG 또는 WebP 파일 하나를 업로드하고 다음 생성 요청에서 사용할 imageAssetId를 받습니다.")
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
