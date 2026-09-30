package com.ssafy.c206.backend.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Optional;

import com.ssafy.c206.backend.common.api.DomainException;
import com.ssafy.c206.backend.common.api.ErrorCode;
import com.ssafy.c206.backend.common.time.ServiceClock;
import com.ssafy.c206.backend.user.agreement.domain.CopyrightAgreement;
import com.ssafy.c206.backend.user.agreement.repository.CopyrightAgreementRepository;
import com.ssafy.c206.backend.user.agreement.service.CopyrightAgreementQueryService;
import org.junit.jupiter.api.Test;

class CopyrightAgreementQueryServiceTest {

	private static final Instant REQUEST_TIME = Instant.parse("2026-08-05T00:00:00Z");

	private final CopyrightAgreementRepository repository = mock(CopyrightAgreementRepository.class);
	private final ServiceClock serviceClock = new ServiceClock(
			Clock.fixed(REQUEST_TIME, ZoneId.of("UTC")), ZoneId.of("Asia/Seoul"));
	private final CopyrightAgreementQueryService service =
			new CopyrightAgreementQueryService(repository, serviceClock);

	@Test
	void directCallWithoutConsentIsRejectedBeforeAgreementLookup() {
		assertThatThrownBy(() -> service.requireCurrent(1L, false, REQUEST_TIME))
				.isInstanceOfSatisfying(DomainException.class,
						exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR));
		verifyNoInteractions(repository);
	}

	@Test
	void staleAgreementIdHasDedicatedConflictCode() {
		CopyrightAgreement current = mock(CopyrightAgreement.class);
		when(current.getId()).thenReturn(1L);
		when(repository.findFirstByActiveTrueAndEffectiveAtLessThanEqualOrderByEffectiveAtDescIdDesc(REQUEST_TIME))
				.thenReturn(Optional.of(current));

		assertThatThrownBy(() -> service.requireCurrent(9999L, true, REQUEST_TIME))
				.isInstanceOfSatisfying(DomainException.class,
						exception -> assertThat(exception.getErrorCode())
								.isEqualTo(ErrorCode.COPYRIGHT_AGREEMENT_MISMATCH));
	}

	@Test
	void currentReadUsesNotFoundWhenNoAgreementIsAvailable() {
		assertThatThrownBy(service::getCurrent)
				.isInstanceOfSatisfying(DomainException.class,
						exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.NOT_FOUND));
	}

	@Test
	void registrationValidationUsesServiceUnavailableWhenNoAgreementIsAvailable() {
		assertThatThrownBy(() -> service.requireCurrent(1L, true, REQUEST_TIME))
				.isInstanceOfSatisfying(DomainException.class,
						exception -> assertThat(exception.getErrorCode())
								.isEqualTo(ErrorCode.COPYRIGHT_AGREEMENT_UNAVAILABLE));
	}
}
