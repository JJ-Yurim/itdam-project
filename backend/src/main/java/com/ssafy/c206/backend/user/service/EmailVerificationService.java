package com.ssafy.c206.backend.user.service;

import java.security.SecureRandom;
import java.util.UUID;

import com.ssafy.c206.backend.common.api.DomainException;
import com.ssafy.c206.backend.common.api.ErrorCode;
import com.ssafy.c206.backend.user.api.EmailVerificationSendResponse;
import com.ssafy.c206.backend.user.domain.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class EmailVerificationService {

	private final EmailVerificationStore store;
	private final VerificationMailDispatcher mailDispatcher;
	private final EmailVerificationProperties properties;
	private final SecureRandom random = new SecureRandom();

	public EmailVerificationSendResponse sendCode(String rawEmail, String clientIp) {
		String email = normalizeEmail(rawEmail);
		// IP 상한을 먼저 검사한다. 쿨다운을 먼저 선점하면 상한에 걸린 사용자가 코드도 못 받은 채 60초를 기다리게 된다.
		if (store.incrementIpSendCount(clientIp, properties.ipWindow()) > properties.ipHourlyLimit()) {
			throw new DomainException(ErrorCode.RATE_LIMITED, "Too many verification requests from this address.");
		}
		// 쿨다운 선점(SETNX)이 동시 요청의 중복 발송을 막는다.
		if (!store.tryStartCooldown(email, properties.resendCooldown())) {
			throw new DomainException(ErrorCode.RATE_LIMITED, "Please wait before requesting another code.");
		}
		String code = generateCode();
		store.saveCode(email, code, properties.codeTtl());
		mailDispatcher.dispatch(email, code);
		return new EmailVerificationSendResponse(
				properties.codeTtl().toSeconds(), properties.resendCooldown().toSeconds());
	}

	/**
	 * 코드 확인에 성공하면 1회용 검증 토큰을 발급한다.
	 * 가입 요청은 이 토큰을 함께 제출해야 하므로, 인증을 수행한 클라이언트만 가입할 수 있다.
	 */
	public ConfirmResult confirm(String rawEmail, String code) {
		String email = normalizeEmail(rawEmail);
		String storedCode = store.findCode(email).orElseThrow(
				() -> new DomainException(ErrorCode.NOT_FOUND, "Verification code was not issued or has expired."));
		if (!storedCode.equals(code)) {
			if (store.incrementFailedAttempts(email, properties.codeTtl()) >= properties.maxAttempts()) {
				store.removeCode(email);
				throw new DomainException(ErrorCode.RATE_LIMITED,
						"Too many incorrect attempts. Request a new code.");
			}
			throw new DomainException(ErrorCode.VALIDATION_ERROR, "Verification code does not match.");
		}
		store.removeCode(email);
		String verificationToken = UUID.randomUUID().toString();
		store.markVerified(email, verificationToken, properties.verifiedTtl());
		return new ConfirmResult(verificationToken, properties.verifiedTtl().toSeconds());
	}

	/** 토큰과 유효 시간이 같은 시점의 값임을 타입으로 보장한다. */
	public record ConfirmResult(String verificationToken, long validForSeconds) {
	}

	private String generateCode() {
		return String.format("%06d", random.nextInt(1_000_000));
	}

	// 인증 표식 키가 가입·로그인과 같은 이메일로 잡히도록 정규화 규칙을 공유한다.
	private static String normalizeEmail(String email) {
		return User.normalizeEmail(email);
	}
}
