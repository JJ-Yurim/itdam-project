package com.ssafy.c206.backend.user.agreement.service;

import java.time.Instant;
import java.util.Optional;

import com.ssafy.c206.backend.common.api.DomainException;
import com.ssafy.c206.backend.common.api.ErrorCode;
import com.ssafy.c206.backend.common.time.ServiceClock;
import com.ssafy.c206.backend.user.agreement.api.CopyrightAgreementResponse;
import com.ssafy.c206.backend.user.agreement.domain.CopyrightAgreement;
import com.ssafy.c206.backend.user.agreement.repository.CopyrightAgreementRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CopyrightAgreementQueryService {

	private static final String CONSENT_REQUIRED_MESSAGE =
			"Consent to the current copyright agreement is required.";

	private final CopyrightAgreementRepository copyrightAgreementRepository;
	private final ServiceClock serviceClock;

	@Transactional(readOnly = true)
	public CopyrightAgreementResponse getCurrent() {
		CopyrightAgreement current = findCurrent(serviceClock.now())
				.orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND,
						"The current copyright agreement was not found."));
		return CopyrightAgreementResponse.from(current);
	}

	@Transactional(readOnly = true)
	public CopyrightAgreement requireCurrent(Long agreementId, boolean agreed, Instant requestTime) {
		if (!agreed || agreementId == null) {
			throw new DomainException(ErrorCode.VALIDATION_ERROR, CONSENT_REQUIRED_MESSAGE);
		}
		CopyrightAgreement current = findCurrent(requestTime)
				.orElseThrow(() -> new DomainException(ErrorCode.COPYRIGHT_AGREEMENT_UNAVAILABLE));
		if (!current.getId().equals(agreementId)) {
			throw new DomainException(ErrorCode.COPYRIGHT_AGREEMENT_MISMATCH);
		}
		return current;
	}

	private Optional<CopyrightAgreement> findCurrent(Instant requestTime) {
		return copyrightAgreementRepository
				.findFirstByActiveTrueAndEffectiveAtLessThanEqualOrderByEffectiveAtDescIdDesc(requestTime);
	}
}
