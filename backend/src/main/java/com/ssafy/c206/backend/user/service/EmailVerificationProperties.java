package com.ssafy.c206.backend.user.service;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** Policy knobs for the pre-signup email ownership verification flow. */
@ConfigurationProperties(prefix = "email.verification")
public record EmailVerificationProperties(
	@DefaultValue("memory") String store,
	@DefaultValue("logging") String mail,
	@DefaultValue("5m") Duration codeTtl,
	@DefaultValue("10m") Duration verifiedTtl,
	@DefaultValue("60s") Duration resendCooldown,
	@DefaultValue("5") int maxAttempts,
	@DefaultValue("10") int ipHourlyLimit,
	@DefaultValue("1h") Duration ipWindow
) {
}
