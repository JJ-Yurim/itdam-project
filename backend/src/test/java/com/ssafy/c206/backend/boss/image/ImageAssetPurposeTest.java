package com.ssafy.c206.backend.boss.image;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import com.ssafy.c206.backend.boss.image.domain.ImageAsset;
import com.ssafy.c206.backend.boss.image.domain.ImagePurpose;
import com.ssafy.c206.backend.user.domain.User;
import org.junit.jupiter.api.Test;

class ImageAssetPurposeTest {

	@Test
	void purposeIsExplicitAndIndependentOfStorageKey() {
		User owner = User.createFixture("purpose@test.local", "hash", "purpose-owner");
		ImageAsset asset = ImageAsset.temporary("legacy-or-external-key.png", "image/png", 10,
				ImagePurpose.BOOK_COVER, owner, Instant.parse("2026-08-04T00:00:00Z"));

		assertThat(asset.isBookCover()).isTrue();
		assertThat(asset.isBossPhase()).isFalse();
	}
}
