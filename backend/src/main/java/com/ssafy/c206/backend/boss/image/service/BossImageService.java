package com.ssafy.c206.backend.boss.image.service;

import com.ssafy.c206.backend.boss.image.api.BossImageUploadResponse;
import com.ssafy.c206.backend.boss.image.domain.ImageAsset;
import com.ssafy.c206.backend.boss.image.domain.ImagePurpose;
import com.ssafy.c206.backend.boss.image.repository.ImageAssetRepository;
import com.ssafy.c206.backend.boss.image.storage.ImageStorage;
import com.ssafy.c206.backend.boss.image.storage.StoredImage;
import com.ssafy.c206.backend.common.security.AuthenticatedUser;
import com.ssafy.c206.backend.common.security.AuthenticatedUsers;
import com.ssafy.c206.backend.common.time.ServiceClock;
import com.ssafy.c206.backend.user.domain.User;
import com.ssafy.c206.backend.user.service.UserQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class BossImageService {

	private final ImageStorage imageStorage;
	private final ImageAssetRepository imageAssetRepository;
	private final UserQueryService userQueryService;
	private final BossImageUrlResolver urlResolver;
	private final ServiceClock serviceClock;

	@Transactional
	public BossImageUploadResponse upload(AuthenticatedUser principal, MultipartFile file) {
		return upload(principal, file, ImagePurpose.BOSS_PHASE);
	}

	@Transactional
	public BossImageUploadResponse uploadBookCover(AuthenticatedUser principal, MultipartFile file) {
		return upload(principal, file, ImagePurpose.BOOK_COVER);
	}

	private BossImageUploadResponse upload(AuthenticatedUser principal, MultipartFile file, ImagePurpose purpose) {
		long userId = AuthenticatedUsers.requireUserId(principal);
		User uploader = userQueryService.getActiveUser(userId);
		StoredImage stored = imageStorage.store(file, purpose);
		try {
			ImageAsset asset = imageAssetRepository.saveAndFlush(ImageAsset.temporary(
					stored.storageKey(), stored.contentType(), stored.byteSize(), purpose, uploader, serviceClock.now()));
			return BossImageUploadResponse.from(asset, urlResolver.publicUrl(asset));
		} catch (RuntimeException exception) {
			try {
				imageStorage.delete(stored.storageKey());
			} catch (RuntimeException cleanupFailure) {
				exception.addSuppressed(cleanupFailure);
			}
			throw exception;
		}
	}
}
