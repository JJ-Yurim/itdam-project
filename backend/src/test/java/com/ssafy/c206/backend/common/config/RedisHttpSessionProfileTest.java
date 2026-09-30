package com.ssafy.c206.backend.common.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.session.SessionRepository;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class RedisHttpSessionProfileTest {

	@Autowired
	private ApplicationContext applicationContext;

	@Test
	void testProfileKeepsContainerLocalServletSessions() {
		assertThat(applicationContext.containsBean("springSessionRepositoryFilter")).isFalse();
		assertThat(applicationContext.getBeansOfType(SessionRepository.class)).isEmpty();
	}
}
