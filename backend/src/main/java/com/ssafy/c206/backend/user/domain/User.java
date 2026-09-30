package com.ssafy.c206.backend.user.domain;

import java.util.Locale;
import java.time.Instant;

import com.ssafy.c206.backend.user.agreement.domain.CopyrightAgreement;
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
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "users", indexes = @Index(
		name = "idx_users_copyright_agreement_id", columnList = "copyright_agreement_id"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@NotBlank
	@Email
	@Size(max = 255)
	@Column(nullable = false, unique = true, length = 255)
	private String email;

	// 소셜 가입 계정은 비밀번호가 없다. 비밀번호 로그인은 null 해시를 인증 실패로 처리한다.
	@Size(max = 255)
	@Column(name = "password_hash", length = 255)
	private String passwordHash;

	@NotNull
	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(nullable = false, length = 20)
	private AuthProvider provider;

	@Size(max = 255)
	@Column(name = "provider_id", length = 255)
	private String providerId;

	@NotBlank
	@Size(min = 1, max = 100)
	@Column(nullable = false, unique = true, length = 100)
	private String nickname;

	@NotNull
	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(nullable = false, length = 30)
	private UserStatus status;

	@NotNull
	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(nullable = false, length = 20)
	private UserRole role;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "copyright_agreement_id")
	private CopyrightAgreement copyrightAgreement;

	@Column(name = "copyright_agreed_at")
	private Instant copyrightAgreedAt;

	@Column(name = "copyright_agreement_expires_at")
	private Instant copyrightAgreementExpiresAt;

	@Column(nullable = false, updatable = false)
	private Instant createdAt;

	@Column(nullable = false)
	private Instant updatedAt;

	private User(String email, String passwordHash, String nickname, UserStatus status, UserRole role) {
		this(email, passwordHash, nickname, status, role, AuthProvider.LOCAL, null);
	}

	private User(String email, String passwordHash, String nickname, UserStatus status, UserRole role,
			AuthProvider provider, String providerId) {
		this.email = normalizeEmail(email);
		this.passwordHash = passwordHash;
		this.nickname = normalizeNickname(nickname);
		this.status = status;
		this.role = role;
		this.provider = provider;
		this.providerId = providerId;
	}

	public static User createFixture(String email, String passwordHash, String nickname) {
		return new User(email, passwordHash, nickname, UserStatus.ACTIVE, UserRole.USER);
	}

	public static User register(String email, String passwordHash, String nickname,
			CopyrightAgreement copyrightAgreement, Instant copyrightAgreedAt,
			Instant copyrightAgreementExpiresAt) {
		User user = new User(email, passwordHash, nickname, UserStatus.ACTIVE, UserRole.USER);
		user.copyrightAgreement = copyrightAgreement;
		user.copyrightAgreedAt = copyrightAgreedAt;
		user.copyrightAgreementExpiresAt = copyrightAgreementExpiresAt;
		return user;
	}

	public static User registerAdmin(String email, String passwordHash, String nickname) {
		return new User(email, passwordHash, nickname, UserStatus.ACTIVE, UserRole.ADMIN);
	}

	public static User registerSocial(AuthProvider provider, String providerId, String email, String nickname) {
		return new User(email, null, nickname, UserStatus.ACTIVE, UserRole.USER, provider, providerId);
	}

	public static User registerSocial(AuthProvider provider, String providerId, String email, String nickname,
			CopyrightAgreement copyrightAgreement, Instant copyrightAgreedAt,
			Instant copyrightAgreementExpiresAt) {
		User user = registerSocial(provider, providerId, email, nickname);
		user.copyrightAgreement = copyrightAgreement;
		user.copyrightAgreedAt = copyrightAgreedAt;
		user.copyrightAgreementExpiresAt = copyrightAgreementExpiresAt;
		return user;
	}

	public static User createFixture(String email, String passwordHash, String nickname, UserStatus status) {
		return new User(email, passwordHash, nickname, status, UserRole.USER);
	}

	/**
	 * 이메일 정규화 단일 출처. 저장·조회·인증(코드 표식 키)이 모두 같은 규칙을 쓴다.
	 * Gmail은 +tag 별칭과 googlemail.com 도메인이 같은 편지함이므로 중복 가입 비교를 위해 통일한다.
	 * 로컬파트의 점(.)도 Gmail은 무시하지만, 다른 도메인과 규칙이 갈리고 표시 주소와의 괴리가 커져
	 * 과잉 통합을 피하려고 의도적으로 제외했다. 규칙을 바꾸면 기존 행 소급 마이그레이션이 필요하다(V5 참고).
	 */
	public static String normalizeEmail(String value) {
		if (value == null) {
			return null;
		}
		String normalized = value.trim().toLowerCase(Locale.ROOT);
		int atIndex = normalized.lastIndexOf('@');
		if (atIndex <= 0) {
			return normalized;
		}
		String localPart = normalized.substring(0, atIndex);
		String domain = normalized.substring(atIndex + 1);
		if (domain.equals("googlemail.com")) {
			domain = "gmail.com";
		}
		if (domain.equals("gmail.com")) {
			int plusIndex = localPart.indexOf('+');
			if (plusIndex > 0) {
				localPart = localPart.substring(0, plusIndex);
			}
		}
		return localPart + "@" + domain;
	}

	private static String normalizeNickname(String value) {
		return value == null ? null : value.trim();
	}

	@PrePersist
	void onCreate() {
		// CONVENTIONS §7 예외: 감사 시각 기본값. 도메인이 시각을 넣었으면 덮지 않는다.
		if (createdAt == null) {
			createdAt = Instant.now();
		}
		if (updatedAt == null) {
			updatedAt = createdAt;
		}
	}

	@PreUpdate
	void onUpdate() {
		updatedAt = Instant.now();
	}

}
