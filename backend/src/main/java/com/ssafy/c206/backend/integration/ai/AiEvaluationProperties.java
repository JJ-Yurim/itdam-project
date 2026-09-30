package com.ssafy.c206.backend.integration.ai;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** Configuration for the Spring-to-AI evaluation transport. */
@ConfigurationProperties(prefix = "ai.evaluation")
public record AiEvaluationProperties(
	@DefaultValue("fake") String client,
	@DefaultValue("http://localhost:8001") String baseUrl,
	@DefaultValue("sentence-evaluation-v1") String contractVersion,
	@DefaultValue("2s") Duration connectTimeout,
	@DefaultValue("30s") Duration readTimeout
) {
}
