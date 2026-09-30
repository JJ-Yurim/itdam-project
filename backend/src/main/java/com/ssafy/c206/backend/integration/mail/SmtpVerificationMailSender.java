package com.ssafy.c206.backend.integration.mail;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@ConditionalOnProperty(prefix = "email.verification", name = "mail", havingValue = "smtp")
public class SmtpVerificationMailSender implements VerificationMailSender {

	private final JavaMailSender mailSender;
	private final String from;

	public SmtpVerificationMailSender(JavaMailSender mailSender,
			@Value("${spring.mail.username:}") String from) {
		// 자격증명 미주입을 첫 발송 시점의 500이 아니라 기동 실패로 드러낸다(mail 헬스 인디케이터는 꺼져 있음).
		if (!StringUtils.hasText(from)) {
			throw new IllegalStateException(
					"spring.mail.username (SMTP_USERNAME) must be set when email.verification.mail=smtp");
		}
		this.mailSender = mailSender;
		this.from = from;
	}

	@Override
	public void send(String email, String code, Duration validity) {
		SimpleMailMessage message = new SimpleMailMessage();
		message.setFrom(from);
		message.setTo(email);
		message.setSubject("[잇담] 이메일 인증 코드");
		message.setText("잇담 이메일 인증 코드입니다.\n\n인증 코드: " + code + "\n\n"
				+ validity.toMinutes() + "분 안에 입력해 주세요. 직접 요청하지 않았다면 이 메일을 무시하세요.");
		mailSender.send(message);
	}
}
