package com.ssafy.c206.backend.integration.ai;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * ai.evaluation.client=http 설정에서 HTTP 전송 스택이 정상 배선되는지 검증한다.
 * RestClient.Builder 자동 구성 누락 같은 기동 실패를 테스트 단계에서 잡는다.
 */
@SpringBootTest(properties = "ai.evaluation.client=http")
@ActiveProfiles("test")
class AiEvaluationClientWiringTest {

	@Autowired
	private AiEvaluationClient client;

	@Test
	void httpClientIsSelectedWhenConfigured() {
		assertThat(client).isInstanceOf(HttpAiEvaluationClient.class);
	}
}
