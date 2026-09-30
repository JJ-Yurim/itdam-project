package com.ssafy.c206.backend.boss.image;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

import com.ssafy.c206.backend.boss.image.repository.ImageAssetRepository;
import com.ssafy.c206.backend.boss.image.domain.ImagePurpose;
import com.ssafy.c206.backend.boss.image.service.BossImageService;
import com.ssafy.c206.backend.boss.image.service.BossImageUrlResolver;
import com.ssafy.c206.backend.boss.image.storage.ImageStorage;
import com.ssafy.c206.backend.boss.image.storage.StoredImage;
import com.ssafy.c206.backend.common.security.AuthenticatedUser;
import com.ssafy.c206.backend.common.time.ServiceClock;
import com.ssafy.c206.backend.user.domain.User;
import com.ssafy.c206.backend.user.service.UserQueryService;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

class BossImageServiceTest {

	@Test
	void deletesStoredFileWhenMetadataSaveFails() {
		ImageStorage storage = mock(ImageStorage.class);
		ImageAssetRepository repository = mock(ImageAssetRepository.class);
		UserQueryService userQueryService = mock(UserQueryService.class);
		User admin = User.registerAdmin("admin@example.com", "encoded", "admin");
		StoredImage stored = new StoredImage("boss-phases/generated.png", "image/png", 68L);
		when(storage.store(org.mockito.ArgumentMatchers.any(),
				org.mockito.ArgumentMatchers.eq(ImagePurpose.BOSS_PHASE)))
				.thenReturn(stored);
		when(userQueryService.getActiveUser(1L)).thenReturn(admin);
		when(repository.saveAndFlush(org.mockito.ArgumentMatchers.any())).thenThrow(new IllegalStateException("db failed"));
		BossImageService service = new BossImageService(storage, repository, userQueryService,
				new BossImageUrlResolver("/images", "/images/system/boss-phase-placeholder.svg"),
				new ServiceClock(Clock.fixed(Instant.parse("2026-08-03T00:00:00Z"), ZoneId.of("UTC")),
						ZoneId.of("Asia/Seoul")));

		assertThatThrownBy(() -> service.upload(new AuthenticatedUser(1L, "admin@example.com"),
				new MockMultipartFile("image", "boss.png", "image/png", new byte[] {1})))
				.isInstanceOf(IllegalStateException.class);
		verify(storage).delete("boss-phases/generated.png");
	}
}
