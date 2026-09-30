package com.ssafy.c206.backend.user.service;

import java.time.Instant;

import com.ssafy.c206.backend.common.api.DomainException;
import com.ssafy.c206.backend.common.api.ErrorCode;
import com.ssafy.c206.backend.common.security.AuthenticatedUser;
import com.ssafy.c206.backend.common.time.ServiceClock;
import com.ssafy.c206.backend.user.agreement.domain.CopyrightAgreement;
import com.ssafy.c206.backend.user.agreement.service.CopyrightAgreementQueryService;
import com.ssafy.c206.backend.user.api.SocialRegistrationRequest;
import com.ssafy.c206.backend.user.api.UserResponse;
import com.ssafy.c206.backend.user.domain.User;
import com.ssafy.c206.backend.user.repository.UserRepository;
import com.ssafy.c206.backend.user.service.SessionAuthenticationService.AuthenticationResult;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SocialRegistrationService {

	private final UserRepository userRepository;
	private final CopyrightAgreementQueryService copyrightAgreementQueryService;
	private final ServiceClock serviceClock;

	@Transactional
	public RegistrationResult register(PendingSocialRegistration pending, SocialRegistrationRequest request) {
		Instant now = serviceClock.now();
		CopyrightAgreement agreement = copyrightAgreementQueryService.requireCurrent(
				request.copyrightAgreementId(), Boolean.TRUE.equals(request.copyrightAgreed()), now);
		String nickname = request.nickname().trim();

		if (userRepository.existsByEmail(pending.email())
				|| userRepository.existsByNickname(nickname)
				|| userRepository.findByProviderAndProviderId(pending.provider(), pending.providerId()).isPresent()) {
			throw conflict();
		}

		try {
			User saved = userRepository.saveAndFlush(User.registerSocial(
					pending.provider(), pending.providerId(), pending.email(), nickname,
					agreement, now, agreement.expiresAt(now, serviceClock.zoneId())));
			AuthenticationResult authentication = new AuthenticationResult(
					new AuthenticatedUser(saved.getId(), saved.getEmail()), saved.getRole());
			return new RegistrationResult(authentication, UserResponse.from(saved));
		} catch (DataIntegrityViolationException exception) {
			throw conflict();
		}
	}

	private DomainException conflict() {
		return new DomainException(ErrorCode.CONFLICT,
				"Email, nickname, or social account is already in use.");
	}

	public record RegistrationResult(AuthenticationResult authentication, UserResponse user) {
	}
}
