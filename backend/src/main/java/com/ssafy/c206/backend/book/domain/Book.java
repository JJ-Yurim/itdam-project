package com.ssafy.c206.backend.book.domain;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import com.ssafy.c206.backend.boss.domain.BossRun;
import com.ssafy.c206.backend.boss.image.domain.ImageAsset;
import com.ssafy.c206.backend.common.api.DomainException;
import com.ssafy.c206.backend.common.api.ErrorCode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "books")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Book {

	/** ai-service의 MAX_SUMMARY_LENGTH와 같아야 한다. 어긋나면 정상 결과가 저장 단계에서 거부된다. */
	public static final int MAX_SUMMARY_LENGTH = 500;

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@OneToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "boss_run_id", nullable = false, unique = true)
	private BossRun bossRun;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "cover_image_asset_id")
	private ImageAsset coverImageAsset;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private BookStatus status;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private BookVisibility visibility;

	@Column(nullable = false, updatable = false)
	private Instant createdAt;

	@Column(nullable = false)
	private Instant updatedAt;

	@Column
	private Instant completedAt;

	/** ai-service가 문단까지 구성해 내려준 표시용 전문. 완성 전에는 비어 있다. */
	@Column(columnDefinition = "text")
	private String body;

	/** 도서관 목록에 노출하는 3~4문장 요약. 요약 계약 이전에 만들어진 책은 비어 있다. */
	@Column(length = MAX_SUMMARY_LENGTH)
	private String summary;

	private Book(BossRun bossRun, ImageAsset coverImageAsset, Instant now) {
		if (bossRun == null) {
			throw new DomainException(ErrorCode.VALIDATION_ERROR, "bossRun and time are required.");
		}
		now = requireTime(now);
		this.bossRun = bossRun;
		this.coverImageAsset = coverImageAsset;
		this.status = BookStatus.GENERATING;
		this.visibility = BookVisibility.HIDDEN;
		this.createdAt = now;
		this.updatedAt = now;
	}

	public static Book generating(BossRun bossRun, Instant now) {
		return new Book(bossRun, null, now);
	}

	public static Book generating(BossRun bossRun, ImageAsset coverImageAsset, Instant now) {
		return new Book(bossRun, coverImageAsset, now);
	}

	public void changeCover(ImageAsset coverImageAsset, Instant now) {
		if (coverImageAsset == null) {
			throw new DomainException(ErrorCode.VALIDATION_ERROR, "cover image is required.");
		}
		now = requireTime(now);
		this.coverImageAsset = coverImageAsset;
		this.updatedAt = now;
	}

	/**
	 * 본문과 함께 완성 처리한다.
	 *
	 * <p>본문을 인자로 받아야 "본문 없는 COMPLETED"가 타입상 만들어지지 않는다. 도서관은
	 * COMPLETED만 노출하므로 그 상태에 본문이 비어 있으면 독자에게 빈 책이 보인다.
	 * 요약은 계약 이전 버전의 ai-service가 보내지 않을 수 있어 null을 허용한다.
	 */
	public void complete(Instant now, String body, String summary) {
		now = requireTime(now);
		if (status == BookStatus.FAILED) {
			throw new DomainException(ErrorCode.CONFLICT, "A failed book cannot be completed directly.");
		}
		if (body == null || body.isBlank()) {
			throw new DomainException(ErrorCode.VALIDATION_ERROR, "A completed book requires a body.");
		}
		// 코드포인트로 센다. String.length()는 UTF-16 단위라 이모지 같은 서로게이트 페어가
		// 섞이면 ai-service(문자 수 기준)와 Postgres VARCHAR(문자 수 기준)는 통과한 값을
		// 여기서만 거부해 정상 결과를 버리게 된다.
		if (summary != null && summary.codePointCount(0, summary.length()) > MAX_SUMMARY_LENGTH) {
			throw new DomainException(ErrorCode.VALIDATION_ERROR, "Book summary is too long.");
		}
		this.body = body;
		this.summary = summary;
		status = BookStatus.COMPLETED;
		visibility = BookVisibility.PUBLIC;
		completedAt = now;
		updatedAt = now;
	}

	/** 완료된 책만 서비스에 공개할 수 있다. 생성 중·실패 책 공개는 409로 거절한다. */
	public void publish(Instant now) {
		now = requireTime(now);
		if (status != BookStatus.COMPLETED) {
			throw new DomainException(ErrorCode.CONFLICT, "Only a completed book can be published.");
		}
		visibility = BookVisibility.PUBLIC;
		updatedAt = now;
	}

	/**
	 * 완료 전 책은 이미 숨김 상태이고, complete()가 공개로 덮어쓰므로 미리 숨겨두는 결정은
	 * 유지될 수 없다. 의미 없는 성공으로 관리자의 결정이 조용히 뒤집히는 것을 막기 위해
	 * 완료 전 숨김 전환도 409로 거절한다.
	 */
	public void hide(Instant now) {
		now = requireTime(now);
		if (status != BookStatus.COMPLETED) {
			throw new DomainException(ErrorCode.CONFLICT, "Only a completed book can be hidden.");
		}
		visibility = BookVisibility.HIDDEN;
		updatedAt = now;
	}

	public void fail(Instant now) {
		now = requireTime(now);
		status = BookStatus.FAILED;
		updatedAt = now;
	}

	/**
	 * 마이크로초로 절단해 기록한다(ISSUE-173 정책). TIMESTAMP(6) 컬럼이 나노초 값을
	 * 반올림하면 메모리 값과 저장 값이 어긋나 expectedUpdatedAt 비교가 무작위로 실패한다.
	 */
	private static Instant requireTime(Instant now) {
		if (now == null) {
			throw new DomainException(ErrorCode.VALIDATION_ERROR, "time is required.");
		}
		return now.truncatedTo(ChronoUnit.MICROS);
	}
}
