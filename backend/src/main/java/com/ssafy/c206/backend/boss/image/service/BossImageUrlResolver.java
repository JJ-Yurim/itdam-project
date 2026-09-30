package com.ssafy.c206.backend.boss.image.service;

import com.ssafy.c206.backend.boss.image.domain.ImageAsset;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class BossImageUrlResolver {

	private final String publicPrefix;
	private final String placeholderUrl;

	public BossImageUrlResolver(
			@Value("${app.boss-images.public-prefix}") String publicPrefix,
			@Value("${app.boss-images.placeholder-url}") String placeholderUrl) {
		this.publicPrefix = stripTrailingSlash(publicPrefix);
		this.placeholderUrl = placeholderUrl;
	}

	public String publicUrl(ImageAsset imageAsset) {
		return imageAsset == null ? placeholderUrl : publicPrefix + "/" + imageAsset.getStorageKey();
	}

	public String publicUrlOrNull(ImageAsset imageAsset) {
		return imageAsset == null ? null : publicUrl(imageAsset);
	}

	public String publicUrlForStorageKeyOrNull(String storageKey) {
		return storageKey == null || storageKey.isBlank() ? null : publicPrefix + "/" + storageKey;
	}

	private static String stripTrailingSlash(String value) {
		if (value == null || value.isBlank()) return "/images";
		return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
	}
}
