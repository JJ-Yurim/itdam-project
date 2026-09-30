package com.ssafy.c206.backend.boss.image.domain;

import java.time.Instant;

import com.ssafy.c206.backend.common.api.DomainException;
import com.ssafy.c206.backend.common.api.ErrorCode;
import com.ssafy.c206.backend.user.domain.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "image_assets", indexes = @Index(
		name = "idx_image_assets_status_created_at", columnList = "status,created_at"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ImageAsset {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "storage_key", nullable = false, unique = true, length = 500)
	private String storageKey;

	@Column(name = "content_type", nullable = false, length = 100)
	private String contentType;

	@Column(name = "byte_size", nullable = false)
	private long byteSize;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 30)
	private ImagePurpose purpose;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 30)
	private ImageAssetStatus status;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "created_by", nullable = false)
	private User createdBy;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	private ImageAsset(String storageKey, String contentType, long byteSize, ImagePurpose purpose,
			User createdBy, Instant now) {
		this.storageKey = requireText(storageKey, "storageKey");
		this.contentType = requireText(contentType, "contentType");
		if (byteSize <= 0) {
			throw new DomainException(ErrorCode.VALIDATION_ERROR, "byteSize must be positive.");
		}
		if (purpose == null || createdBy == null || now == null) {
			throw new DomainException(ErrorCode.VALIDATION_ERROR, "purpose, createdBy and createdAt are required.");
		}
		this.byteSize = byteSize;
		this.purpose = purpose;
		this.status = ImageAssetStatus.TEMPORARY;
		this.createdBy = createdBy;
		this.createdAt = now;
	}

	public static ImageAsset temporary(String storageKey, String contentType, long byteSize, ImagePurpose purpose,
			User createdBy, Instant now) {
		return new ImageAsset(storageKey, contentType, byteSize, purpose, createdBy, now);
	}

	public void activate() {
		status = ImageAssetStatus.ACTIVE;
	}

	public boolean isTemporaryOwnedBy(long userId) {
		return status == ImageAssetStatus.TEMPORARY && createdBy != null && createdBy.getId() != null
				&& createdBy.getId() == userId;
	}

	public boolean isBookCover() {
		return purpose == ImagePurpose.BOOK_COVER;
	}

	public boolean isBossPhase() {
		return purpose == ImagePurpose.BOSS_PHASE;
	}

	private static String requireText(String value, String field) {
		if (value == null || value.trim().isBlank()) {
			throw new DomainException(ErrorCode.VALIDATION_ERROR, field + " must not be blank.");
		}
		return value.trim();
	}
}
