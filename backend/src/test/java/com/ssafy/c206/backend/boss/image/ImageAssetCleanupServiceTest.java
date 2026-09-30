package com.ssafy.c206.backend.boss.image;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import com.ssafy.c206.backend.boss.image.domain.ImageAsset;
import com.ssafy.c206.backend.boss.image.domain.ImageAssetStatus;
import com.ssafy.c206.backend.boss.image.repository.ImageAssetRepository;
import com.ssafy.c206.backend.boss.image.service.ImageAssetCleanupService;
import com.ssafy.c206.backend.boss.image.storage.ImageStorage;
import com.ssafy.c206.backend.boss.repository.BossPhaseRepository;
import com.ssafy.c206.backend.boss.repository.BossPhaseTemplateRepository;
import com.ssafy.c206.backend.boss.repository.BossRepository;
import com.ssafy.c206.backend.book.service.BookCoverReferenceQueryService;
import com.ssafy.c206.backend.common.time.ServiceClock;
import org.junit.jupiter.api.Test;

class ImageAssetCleanupServiceTest {

	@Test
	void deletesOnlyExpiredUnreferencedAssetsAndKeepsRowsWhenFileDeleteFails() {
		Instant now = Instant.parse("2026-08-04T00:00:00Z");
		ImageAssetRepository assets = mock(ImageAssetRepository.class);
		BossPhaseTemplateRepository templates = mock(BossPhaseTemplateRepository.class);
		BossPhaseRepository phases = mock(BossPhaseRepository.class);
		BossRepository bosses = mock(BossRepository.class);
		BookCoverReferenceQueryService bookCoverReferences = mock(BookCoverReferenceQueryService.class);
		ImageStorage storage = mock(ImageStorage.class);
		ServiceClock clock = mock(ServiceClock.class);
		when(clock.now()).thenReturn(now);
		ImageAsset expired = asset(1L, ImageAssetStatus.TEMPORARY, now.minus(Duration.ofDays(2)), "expired.png");
		ImageAsset referenced = asset(2L, ImageAssetStatus.ACTIVE, now.minus(Duration.ofDays(40)), "referenced.png");
		ImageAsset failed = asset(3L, ImageAssetStatus.ACTIVE, now.minus(Duration.ofDays(40)), "failed.png");
		ImageAsset bossCover = asset(4L, ImageAssetStatus.ACTIVE, now.minus(Duration.ofDays(40)), "boss-cover.png");
		ImageAsset bookCover = asset(5L, ImageAssetStatus.ACTIVE, now.minus(Duration.ofDays(40)), "book-cover.png");
		when(assets.findCleanupCandidateIds(now.minus(Duration.ofHours(24)), now.minus(Duration.ofDays(30))))
				.thenReturn(List.of(1L, 2L, 3L, 4L, 5L));
		when(assets.findByIdForUpdate(1L)).thenReturn(Optional.of(expired));
		when(assets.findByIdForUpdate(2L)).thenReturn(Optional.of(referenced));
		when(assets.findByIdForUpdate(3L)).thenReturn(Optional.of(failed));
		when(assets.findByIdForUpdate(4L)).thenReturn(Optional.of(bossCover));
		when(assets.findByIdForUpdate(5L)).thenReturn(Optional.of(bookCover));
		when(templates.existsByImageAssetId(2L)).thenReturn(true);
		when(bosses.existsByBookCoverImageAssetId(4L)).thenReturn(true);
		when(bookCoverReferences.isCoverImageAssetReferenced(5L)).thenReturn(true);
		doThrow(new IllegalStateException("disk unavailable")).when(storage).delete("failed.png");

		new ImageAssetCleanupService(assets, templates, phases, bosses, bookCoverReferences, storage, clock,
				Duration.ofHours(24), Duration.ofDays(30)).cleanup();

		verify(storage).delete("expired.png");
		verify(assets).delete(expired);
		verify(storage, never()).delete("referenced.png");
		verify(assets, never()).delete(referenced);
		verify(storage).delete("failed.png");
		verify(assets, never()).delete(failed);
		verify(storage, never()).delete("boss-cover.png");
		verify(storage, never()).delete("book-cover.png");
	}

	private ImageAsset asset(Long id, ImageAssetStatus status, Instant createdAt, String storageKey) {
		ImageAsset asset = mock(ImageAsset.class);
		when(asset.getId()).thenReturn(id);
		when(asset.getStatus()).thenReturn(status);
		when(asset.getCreatedAt()).thenReturn(createdAt);
		when(asset.getStorageKey()).thenReturn(storageKey);
		return asset;
	}
}
