package com.ssafy.c206.backend.boss.domain;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import com.ssafy.c206.backend.boss.image.domain.ImageAsset;
import com.ssafy.c206.backend.common.api.DomainException;
import com.ssafy.c206.backend.common.api.ErrorCode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "bosses")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Boss {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@NotBlank
	@Size(max = 150)
	@Column(nullable = false, length = 150)
	private String name;

	@NotBlank
	@Column(nullable = false, columnDefinition = "text")
	private String description;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 30)
	private BossStatus status;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "book_cover_image_asset_id")
	private ImageAsset bookCoverImageAsset;

	@Column(nullable = false, updatable = false)
	private Instant createdAt;

	@Column(nullable = false)
	private Instant updatedAt;

	private Boss(String name, String description, Instant now) {
		now = requireTime(now);
		this.name = requireText(name, "name");
		this.description = requireText(description, "description");
		this.status = BossStatus.ACTIVE;
		this.createdAt = now;
		this.updatedAt = now;
	}

	public static Boss create(String name, String description, Instant now) {
		if (now == null) {
			throw new DomainException(ErrorCode.VALIDATION_ERROR, "created time is required.");
		}
		return new Boss(name, description, now);
	}

	public static Boss create(String name, String description, ImageAsset bookCoverImageAsset, Instant now) {
		if (bookCoverImageAsset == null) {
			throw new DomainException(ErrorCode.VALIDATION_ERROR, "book cover image is required.");
		}
		Boss boss = create(name, description, now);
		boss.bookCoverImageAsset = bookCoverImageAsset;
		return boss;
	}

	public void changeBookCover(ImageAsset imageAsset, Instant now) {
		if (imageAsset == null) {
			throw new DomainException(ErrorCode.VALIDATION_ERROR, "book cover image is required.");
		}
		this.bookCoverImageAsset = imageAsset;
		this.updatedAt = requireTime(now);
	}

	public void update(String name, String description, Instant now) {
		this.name = requireText(name, "name");
		this.description = requireText(description, "description");
		this.updatedAt = requireTime(now);
	}

	public void touch(Instant now) {
		this.updatedAt = requireTime(now);
	}

	public void deactivate(Instant now) {
		this.status = BossStatus.INACTIVE;
		this.updatedAt = requireTime(now);
	}

	public void activate(Instant now) {
		this.status = BossStatus.ACTIVE;
		this.updatedAt = requireTime(now);
	}

	private static String requireText(String value, String field) {
		if (value == null || value.trim().isBlank()) {
			throw new DomainException(ErrorCode.VALIDATION_ERROR, field + " must not be blank.");
		}
		return value.trim();
	}

	private static Instant requireTime(Instant value) {
		if (value == null) {
			throw new DomainException(ErrorCode.VALIDATION_ERROR, "time is required.");
		}
		return value.truncatedTo(ChronoUnit.MICROS);
	}
}
