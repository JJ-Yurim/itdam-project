package com.ssafy.c206.backend.boss.image.service;

import java.time.Duration;
import java.time.Instant;

import com.ssafy.c206.backend.boss.image.domain.ImageAsset;
import com.ssafy.c206.backend.boss.image.domain.ImageAssetStatus;
import com.ssafy.c206.backend.boss.image.repository.ImageAssetRepository;
import com.ssafy.c206.backend.boss.image.storage.ImageStorage;
import com.ssafy.c206.backend.boss.repository.BossPhaseRepository;
import com.ssafy.c206.backend.boss.repository.BossPhaseTemplateRepository;
import com.ssafy.c206.backend.boss.repository.BossRepository;
import com.ssafy.c206.backend.book.service.BookCoverReferenceQueryService;
import com.ssafy.c206.backend.common.time.ServiceClock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ImageAssetCleanupService {

	private static final Logger log = LoggerFactory.getLogger(ImageAssetCleanupService.class);
	private final ImageAssetRepository assets;
	private final BossPhaseTemplateRepository templates;
	private final BossPhaseRepository phases;
	private final BossRepository bosses;
	private final BookCoverReferenceQueryService bookCoverReferences;
	private final ImageStorage storage;
	private final ServiceClock clock;
	private final Duration temporaryRetention;
	private final Duration activeRetention;

	public ImageAssetCleanupService(ImageAssetRepository assets, BossPhaseTemplateRepository templates,
			BossPhaseRepository phases, BossRepository bosses, BookCoverReferenceQueryService bookCoverReferences,
			ImageStorage storage, ServiceClock clock,
			@Value("${app.boss-images.temporary-retention:PT24H}") Duration temporaryRetention,
			@Value("${app.boss-images.unreferenced-active-retention:P30D}") Duration activeRetention) {
		this.assets = assets;
		this.templates = templates;
		this.phases = phases;
		this.bosses = bosses;
		this.bookCoverReferences = bookCoverReferences;
		this.storage = storage;
		this.clock = clock;
		this.temporaryRetention = temporaryRetention;
		this.activeRetention = activeRetention;
	}

	@Transactional
	public void cleanup() {
		Instant now = clock.now();
		Instant temporaryCutoff = now.minus(temporaryRetention);
		Instant activeCutoff = now.minus(activeRetention);
		for (Long id : assets.findCleanupCandidateIds(temporaryCutoff, activeCutoff)) {
			ImageAsset asset = assets.findByIdForUpdate(id).orElse(null);
			if (asset == null || !expired(asset, temporaryCutoff, activeCutoff)
					|| templates.existsByImageAssetId(id) || phases.existsByImageAssetId(id)
					|| bosses.existsByBookCoverImageAssetId(id)
					|| bookCoverReferences.isCoverImageAssetReferenced(id)) continue;
			try {
				storage.delete(asset.getStorageKey());
			} catch (RuntimeException exception) {
				log.warn("Boss image cleanup will retry asset {} after storage delete failure", id, exception);
				continue;
			}
			assets.delete(asset);
		}
	}

	private boolean expired(ImageAsset asset, Instant temporaryCutoff, Instant activeCutoff) {
		Instant cutoff = asset.getStatus() == ImageAssetStatus.TEMPORARY ? temporaryCutoff : activeCutoff;
		return asset.getCreatedAt().isBefore(cutoff);
	}
}
