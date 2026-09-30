package com.ssafy.c206.backend.common.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class JacksonConfiguration {

	// Spring Boot 4는 ObjectMapper 빈을 자동 구성하지 않으므로 직접 정의한다.
	// 날짜·시각은 ApiError.occurredAt 등 문서화된 계약(ISO-8601)에 맞춰 직렬화한다.
	@Bean
	ObjectMapper objectMapper() {
		return JsonMapper.builder()
				.findAndAddModules()
				.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
				.build();
	}
}
