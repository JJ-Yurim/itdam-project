package com.ssafy.c206.backend.user.agreement.domain;

import java.time.Instant;
import java.time.ZoneId;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "copyright_agreements")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CopyrightAgreement {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@NotBlank
	@Size(max = 30)
	@Column(nullable = false, unique = true, length = 30)
	private String version;

	@NotBlank
	@Size(max = 200)
	@Column(nullable = false, length = 200)
	private String title;

	@NotBlank
	@Column(nullable = false, columnDefinition = "TEXT")
	private String content;

	@Column(nullable = false)
	private boolean active;

	@NotNull
	@Column(nullable = false)
	private Instant effectiveAt;

	@Positive
	@Column(nullable = false)
	private int validMonths;

	@NotNull
	@Column(nullable = false, updatable = false)
	private Instant createdAt;

	public Instant expiresAt(Instant agreedAt, ZoneId serviceZone) {
		return agreedAt.atZone(serviceZone).plusMonths(validMonths).toInstant();
	}
}
