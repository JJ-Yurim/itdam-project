package com.ssafy.c206.backend.boss.image.service;

import lombok.RequiredArgsConstructor;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ImageAssetCleanupScheduler {

	private final ImageAssetCleanupService cleanupService;

	@Scheduled(cron = "${app.boss-images.cleanup-cron:0 20 4 * * *}", zone = "Asia/Seoul")
	@SchedulerLock(name = "imageAssetCleanup", lockAtLeastFor = "PT5M", lockAtMostFor = "PT2H")
	public void cleanup() {
		cleanupService.cleanup();
	}
}
