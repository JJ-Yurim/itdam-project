package com.ssafy.c206.backend.user.service;

import com.ssafy.c206.backend.integration.mail.VerificationMailSender;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * 발송을 요청 스레드에서 분리한다(202 계약 유지, SMTP 지연 시 톰캣 워커 고갈 방지).
 * 실패하면 저장한 코드와 쿨다운을 되돌려 사용자가 곧바로 재요청할 수 있게 한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class VerificationMailDispatcher {

	private final VerificationMailSender mailSender;
	private final EmailVerificationStore store;
	private final EmailVerificationProperties properties;

	@Async("emailVerificationExecutor")
	public void dispatch(String email, String code) {
		try {
			mailSender.send(email, code, properties.codeTtl());
		} catch (RuntimeException exception) {
			store.removeCode(email);
			store.clearCooldown(email);
			log.error("Verification mail send failed, code and cooldown rolled back: {}", email, exception);
		}
	}
}
