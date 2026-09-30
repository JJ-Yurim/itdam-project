package com.ssafy.c206.backend.user.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;

import com.ssafy.c206.backend.common.api.DomainException;
import com.ssafy.c206.backend.common.api.ErrorCode;
import com.ssafy.c206.backend.common.time.ServiceClock;
import com.ssafy.c206.backend.user.api.SignupRequest;
import com.ssafy.c206.backend.user.api.UserResponse;
import com.ssafy.c206.backend.user.agreement.domain.CopyrightAgreement;
import com.ssafy.c206.backend.user.agreement.service.CopyrightAgreementQueryService;
import com.ssafy.c206.backend.user.domain.User;
import com.ssafy.c206.backend.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
@RequiredArgsConstructor
public class RegistrationService {

	private final UserRepository userRepository;
	private final PasswordEncoder passwordEncoder;
	private final EmailVerificationStore emailVerificationStore;
	private final CopyrightAgreementQueryService copyrightAgreementQueryService;
	private final ServiceClock serviceClock;

	@Transactional
	public UserResponse register(SignupRequest request) {
		String email = User.normalizeEmail(request.email());
		// 서버 측 표식의 토큰과 요청 토큰을 대조한다. 인증을 수행한 클라이언트(confirm 응답 수신자)만 가입할 수 있다.
		// Bean Validation을 거치지 않는 직접 호출 경로가 있으므로 null/blank도 여기서 방어한다.
		String requestToken = request.verificationToken();
		if (requestToken == null || requestToken.isBlank()) {
			throw new DomainException(ErrorCode.EMAIL_NOT_VERIFIED);
		}
		String storedToken = emailVerificationStore.findVerifiedToken(email)
				.orElseThrow(() -> new DomainException(ErrorCode.EMAIL_NOT_VERIFIED));
		if (!MessageDigest.isEqual(storedToken.getBytes(StandardCharsets.UTF_8),
				requestToken.getBytes(StandardCharsets.UTF_8))) {
			throw new DomainException(ErrorCode.EMAIL_NOT_VERIFIED);
		}
		Instant requestTime = serviceClock.now();
		// HTTP 요청의 false/null은 Bean Validation이 먼저 막는다. Boolean 변환과 서비스 검증은
		// 검증 계층을 우회한 직접 호출에서도 동의가 누락되지 않도록 유지하는 방어선이다.
		CopyrightAgreement copyrightAgreement = copyrightAgreementQueryService.requireCurrent(
				request.copyrightAgreementId(), Boolean.TRUE.equals(request.copyrightAgreed()), requestTime);
		Instant copyrightAgreementExpiresAt = copyrightAgreement.expiresAt(requestTime, serviceClock.zoneId());
		String nickname = request.nickname().trim();
		if (userRepository.existsByEmail(email) || userRepository.existsByNickname(nickname)) {
			throw new DomainException(ErrorCode.CONFLICT, "Email or nickname is already in use.");
		}
		try {
			// flush를 명시해 unique 제약 위반이 커밋 시점이 아닌 여기서 드러나 409로 매핑되게 한다.
			UserResponse response = UserResponse.from(userRepository.saveAndFlush(
					User.register(email, passwordEncoder.encode(request.password()), nickname,
							copyrightAgreement, requestTime, copyrightAgreementExpiresAt)));
			consumeVerifiedAfterCommit(email);
			return response;
		} catch (DataIntegrityViolationException exception) {
			throw new DomainException(ErrorCode.CONFLICT, "Email or nickname is already in use.");
		}
	}

	// 표식은 커밋이 확정된 뒤에만 소모한다. Redis/인메모리 store는 트랜잭션에 참여하지 않으므로
	// 트랜잭션 안에서 지우면 롤백 시 가입은 실패했는데 표식만 사라져 재인증을 강요하게 된다.
	private void consumeVerifiedAfterCommit(String email) {
		if (!TransactionSynchronizationManager.isSynchronizationActive()) {
			emailVerificationStore.consumeVerified(email);
			return;
		}
		TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
			@Override
			public void afterCommit() {
				emailVerificationStore.consumeVerified(email);
			}
		});
	}
}
