package com.ssafy.c206.backend.integration.mail;

import java.time.Duration;

/** Sends the email ownership verification code without coupling the domain service to SMTP details. */
public interface VerificationMailSender {

	void send(String email, String code, Duration validity);
}
