package com.ssafy.c206.backend.user;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.ZoneId;

import com.ssafy.c206.backend.common.time.ServiceClock;
import com.ssafy.c206.backend.user.service.EmailVerificationProperties;
import com.ssafy.c206.backend.user.service.InMemoryEmailVerificationStore;
import com.ssafy.c206.backend.user.service.VerificationMailDispatcher;
import org.junit.jupiter.api.Test;
import org.springframework.mail.MailSendException;

class VerificationMailDispatcherTest {

	private static final String EMAIL = "dispatch@example.com";

	private final InMemoryEmailVerificationStore store = new InMemoryEmailVerificationStore(
			new ServiceClock(Clock.systemUTC(), ZoneId.of("Asia/Seoul")));
	private final EmailVerificationProperties properties = new EmailVerificationProperties(
			"memory", "logging", Duration.ofMinutes(5), Duration.ofMinutes(10),
			Duration.ofSeconds(60), 5, 10, Duration.ofHours(1));

	@Test
	void sendFailureRollsBackCodeAndCooldown() {
		store.tryStartCooldown(EMAIL, Duration.ofSeconds(60));
		store.saveCode(EMAIL, "123456", Duration.ofMinutes(5));

		VerificationMailDispatcher dispatcher = new VerificationMailDispatcher(
				(email, code, validity) -> {
					throw new MailSendException("smtp unavailable");
				}, store, properties);
		dispatcher.dispatch(EMAIL, "123456");

		assertThat(store.findCode(EMAIL)).isEmpty();
		assertThat(store.tryStartCooldown(EMAIL, Duration.ofSeconds(60)))
				.as("발송 실패 후에는 쿨다운이 해제되어 즉시 재요청할 수 있다")
				.isTrue();
	}

	@Test
	void successfulSendKeepsCodeAndCooldown() {
		store.tryStartCooldown(EMAIL, Duration.ofSeconds(60));
		store.saveCode(EMAIL, "123456", Duration.ofMinutes(5));

		VerificationMailDispatcher dispatcher = new VerificationMailDispatcher(
				(email, code, validity) -> {
				}, store, properties);
		dispatcher.dispatch(EMAIL, "123456");

		assertThat(store.findCode(EMAIL)).contains("123456");
		assertThat(store.tryStartCooldown(EMAIL, Duration.ofSeconds(60))).isFalse();
	}
}
