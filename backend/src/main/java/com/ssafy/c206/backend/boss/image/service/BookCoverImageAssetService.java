package com.ssafy.c206.backend.boss.image.service;

import com.ssafy.c206.backend.boss.image.domain.ImageAsset;
import com.ssafy.c206.backend.boss.image.domain.ImageAssetStatus;
import com.ssafy.c206.backend.boss.image.repository.ImageAssetRepository;
import com.ssafy.c206.backend.common.api.DomainException;
import com.ssafy.c206.backend.common.api.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(propagation = Propagation.MANDATORY)
public class BookCoverImageAssetService {
	private final ImageAssetRepository imageAssetRepository;

	public ImageAsset resolveAssignable(long userId, Long imageAssetId) {
		// 페이즈 이미지도 imageAssetId라는 이름을 쓰므로 어느 쪽인지 밝힌다. 그냥
		// "imageAssetId is required."라고만 하면 페이즈 이미지 업로드가 실패한 줄 알게 된다.
		if (imageAssetId == null) {
			throw validation("A book cover imageAssetId is required.");
		}
		ImageAsset asset = imageAssetRepository.findByIdForUpdate(imageAssetId)
				.orElseThrow(() -> validation("Unknown book cover imageAssetId: " + imageAssetId));
		if (!asset.isBookCover()) {
			throw validation("The image asset is not a book cover.");
		}
		if (!asset.isTemporaryOwnedBy(userId) && asset.getStatus() != ImageAssetStatus.ACTIVE) {
			throw validation("The book cover must belong to the administrator or already be active.");
		}
		return asset;
	}

	public void activate(ImageAsset asset) {
		if (asset.getStatus() == ImageAssetStatus.TEMPORARY) {
			asset.activate();
		}
	}

	private DomainException validation(String message) {
		return new DomainException(ErrorCode.VALIDATION_ERROR, message);
	}
}
