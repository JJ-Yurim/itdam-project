package com.ssafy.c206.backend.integration.mail;

import java.time.Duration;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

// SMTP 자격증명 없이 기동해야 하는 테스트·로컬 환경용 구현. 실제 발송 대신 로그로 남긴다.
@Slf4j
@Component
@ConditionalOnProperty(prefix = "email.verification", name = "mail", havingValue = "logging", matchIfMissing = true)
public class LoggingVerificationMailSender implements VerificationMailSender {

	@Override
	public void send(String email, String code, Duration validity) {
		log.info("[FAKE MAIL] verification code for {}: {} (valid {} minutes)", email, code, validity.toMinutes());
	}
}
