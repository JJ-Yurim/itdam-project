package com.ssafy.c206.backend.integration.ai;

import java.net.http.HttpClient;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/** Wires the HTTP client without coupling the domain service to transport details. */
// HttpAiEvaluationClient가 선택된 환경에서만 HTTP 전송 스택을 구성한다.
// Fake 환경(test)은 이 설정 없이 기동할 수 있어야 한다.
@Configuration
@ConditionalOnProperty(prefix = "ai.evaluation", name = "client", havingValue = "http")
@EnableConfigurationProperties(AiEvaluationProperties.class)
public class AiEvaluationHttpConfiguration {

	@Bean
	RestClient aiEvaluationRestClient(RestClient.Builder builder, AiEvaluationProperties properties) {
		HttpClient httpClient = HttpClient.newBuilder()
			.connectTimeout(properties.connectTimeout())
			.version(HttpClient.Version.HTTP_1_1)
			.build();
		JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
		requestFactory.setReadTimeout(properties.readTimeout());
		return builder
			.baseUrl(properties.baseUrl())
			.requestFactory(requestFactory)
			.build();
	}
}
