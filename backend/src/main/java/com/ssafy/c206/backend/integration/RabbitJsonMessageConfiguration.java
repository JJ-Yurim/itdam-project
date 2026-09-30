package com.ssafy.c206.backend.integration;

import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Rabbit 연동이 하나라도 켜졌을 때만 등록되는 공용 JSON 변환기.
 *
 * <p>한 곳에 두는 이유: Spring Boot 자동설정은 MessageConverter 빈이 유일할 때만 그것을
 * RabbitTemplate·리스너에 연결한다. 책 생성과 평가가 각자 변환기를 선언하면 두 모드를
 * 함께 켰을 때 빈이 둘이 되어 자동설정이 조용히 기본 변환기로 되돌아가고, JSON 페이로드
 * 역직렬화가 전부 깨진다.
 *
 * <p>신뢰 패키지는 __TypeId__ 헤더 기반 역직렬화(테스트의 왕복 검증)에 필요하다.
 * ai-service가 보내는 실제 메시지는 이 헤더가 없어 리스너 시그니처의 타입으로 변환된다.
 * 판정은 패키지명 완전 일치라 상위 패키지 하나로 하위까지 덮을 수 없다 — 메시지가 사는
 * 패키지를 각각 나열해야 한다.
 */
@Configuration
@ConditionalOnExpression(
		"'${book.generation.client:logging}' == 'rabbit' or '${evaluation.dispatch:sync}' == 'rabbit'")
public class RabbitJsonMessageConfiguration {

	@Bean
	MessageConverter rabbitJsonMessageConverter() {
		return new JacksonJsonMessageConverter(
				"com.ssafy.c206.backend.integration.book",
				"com.ssafy.c206.backend.integration.evaluation");
	}
}
