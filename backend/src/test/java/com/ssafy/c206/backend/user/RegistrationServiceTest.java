package com.ssafy.c206.backend.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

import com.ssafy.c206.backend.common.api.DomainException;
import com.ssafy.c206.backend.common.api.ErrorCode;
import com.ssafy.c206.backend.common.time.ServiceClock;
import com.ssafy.c206.backend.user.api.SignupRequest;
import com.ssafy.c206.backend.user.agreement.domain.CopyrightAgreement;
import com.ssafy.c206.backend.user.agreement.service.CopyrightAgreementQueryService;
import com.ssafy.c206.backend.user.domain.User;
import com.ssafy.c206.backend.user.repository.UserRepository;
import com.ssafy.c206.backend.user.service.InMemoryEmailVerificationStore;
import com.ssafy.c206.backend.user.service.RegistrationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;

class RegistrationServiceTest {

	private static final String EMAIL = "race@example.com";

	private final UserRepository userRepository = mock(UserRepository.class);
	private final PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
	private final InMemoryEmailVerificationStore store = new InMemoryEmailVerificationStore(
			new ServiceClock(Clock.systemUTC(), ZoneId.of("Asia/Seoul")));
	private final CopyrightAgreementQueryService copyrightAgreementQueryService =
			mock(CopyrightAgreementQueryService.class);
	private final CopyrightAgreement copyrightAgreement = mock(CopyrightAgreement.class);
	private final ServiceClock serviceClock = new ServiceClock(
			Clock.fixed(Instant.parse("2026-08-05T00:00:00Z"), ZoneId.of("UTC")), ZoneId.of("Asia/Seoul"));
	private final RegistrationService service = new RegistrationService(userRepository, passwordEncoder, store,
			copyrightAgreementQueryService, serviceClock);

	@BeforeEach
	void setUp() {
		when(passwordEncoder.encode(anyString())).thenReturn("encoded-password");
		when(userRepository.existsByEmail(anyString())).thenReturn(false);
		when(userRepository.existsByNickname(anyString())).thenReturn(false);
		when(copyrightAgreementQueryService.requireCurrent(any(), anyBoolean(), any(Instant.class)))
				.thenReturn(copyrightAgreement);
		when(copyrightAgreement.expiresAt(any(Instant.class), any(ZoneId.class)))
				.thenReturn(Instant.parse("2027-08-05T00:00:00Z"));
	}

	@Test
	void flushTimeConstraintViolationMapsToConflictAndKeepsVerifiedFlag() {
		// 선행 exists 검사를 통과한 뒤(동시 가입 경합) flush 시점 unique 위반이 나는 경로.
		store.markVerified(EMAIL, "valid-token", Duration.ofMinutes(10));
		when(userRepository.saveAndFlush(any(User.class)))
				.thenThrow(new DataIntegrityViolationException("duplicate key"));

		assertThatThrownBy(() -> service.register(
				new SignupRequest(EMAIL, "password123", "race", "valid-token", 1L, true)))
				.isInstanceOfSatisfying(DomainException.class,
						exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.CONFLICT));

		assertThat(store.findVerifiedToken(EMAIL))
				.as("가입이 실패했으므로 인증 표식은 남아 재인증 없이 재시도할 수 있어야 한다")
				.isPresent();
	}

	@Test
	void successfulRegistrationConsumesVerifiedFlag() {
		store.markVerified(EMAIL, "valid-token", Duration.ofMinutes(10));
		when(userRepository.saveAndFlush(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

		service.register(new SignupRequest(EMAIL, "password123", "race", "valid-token", 1L, true));

		// 트랜잭션 동기화가 없는 환경에서는 즉시 소모된다(있으면 afterCommit에서 소모).
		assertThat(store.findVerifiedToken(EMAIL)).isEmpty();
	}

	@Test
	void unverifiedEmailIsRejectedBeforeAnyRepositoryAccess() {
		assertThatThrownBy(() -> service.register(
				new SignupRequest(EMAIL, "password123", "race", "any-token", 1L, true)))
				.isInstanceOfSatisfying(DomainException.class,
						exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.EMAIL_NOT_VERIFIED));
	}

	@Test
	void nullVerificationTokenIsRejectedWithoutServerError() {
		// Bean Validation을 우회하는 직접 호출 경로에서도 500이 아닌 403 계약을 지킨다.
		store.markVerified(EMAIL, "valid-token", Duration.ofMinutes(10));

		assertThatThrownBy(() -> service.register(
				new SignupRequest(EMAIL, "password123", "race", null, 1L, true)))
				.isInstanceOfSatisfying(DomainException.class,
						exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.EMAIL_NOT_VERIFIED));
	}

	@Test
	void mismatchedVerificationTokenIsRejected() {
		store.markVerified(EMAIL, "valid-token", Duration.ofMinutes(10));

		assertThatThrownBy(() -> service.register(
				new SignupRequest(EMAIL, "password123", "race", "wrong-token", 1L, true)))
				.isInstanceOfSatisfying(DomainException.class,
						exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.EMAIL_NOT_VERIFIED));
		assertThat(store.findVerifiedToken(EMAIL)).isPresent();
	}

	@Test
	void copyrightAgreementConsentIsRequired() {
		store.markVerified(EMAIL, "valid-token", Duration.ofMinutes(10));
		when(copyrightAgreementQueryService.requireCurrent(1L, false, serviceClock.now()))
				.thenThrow(new DomainException(ErrorCode.VALIDATION_ERROR,
						"Consent to the current copyright agreement is required."));

		assertThatThrownBy(() -> service.register(
				new SignupRequest(EMAIL, "password123", "race", "valid-token", 1L, false)))
				.isInstanceOfSatisfying(DomainException.class,
						exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR));
		assertThat(store.findVerifiedToken(EMAIL)).isPresent();
	}
}
