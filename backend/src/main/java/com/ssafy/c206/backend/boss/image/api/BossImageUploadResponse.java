package com.ssafy.c206.backend.boss.image.api;

import com.ssafy.c206.backend.boss.image.domain.ImageAsset;
import io.swagger.v3.oas.annotations.media.Schema;

public record BossImageUploadResponse(
		@Schema(description = "업로드 이미지 자산 식별자입니다.", example = "101") Long imageAssetId,
		@Schema(description = "Nginx가 제공하는 공개 이미지 URL입니다.", example = "/images/boss-phases/example.webp") String imageUrl) {

	public static BossImageUploadResponse from(ImageAsset asset, String imageUrl) {
		return new BossImageUploadResponse(asset.getId(), imageUrl);
	}
}
