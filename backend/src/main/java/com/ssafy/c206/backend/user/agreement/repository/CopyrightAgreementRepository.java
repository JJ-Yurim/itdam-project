package com.ssafy.c206.backend.user.agreement.repository;

import java.time.Instant;
import java.util.Optional;

import com.ssafy.c206.backend.user.agreement.domain.CopyrightAgreement;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CopyrightAgreementRepository extends JpaRepository<CopyrightAgreement, Long> {

	Optional<CopyrightAgreement> findFirstByActiveTrueAndEffectiveAtLessThanEqualOrderByEffectiveAtDescIdDesc(
			Instant requestTime);
}
