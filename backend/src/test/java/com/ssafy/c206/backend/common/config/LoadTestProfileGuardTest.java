package com.ssafy.c206.backend.common.config;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

class LoadTestProfileGuardTest {

	@Test
	void refusesToStartWhenCombinedWithADeploymentProfile() {
		// 한도 무력화는 조용히 진행되므로, 배포 프로필과 섞인 기동은 즉시 실패해야 한다.
		assertThatThrownBy(() -> new LoadTestProfileGuard(profiles("develop", "loadtest")))
				.isInstanceOf(IllegalStateException.class).hasMessageContaining("develop");
		assertThatThrownBy(() -> new LoadTestProfileGuard(profiles("prod", "loadtest")))
				.isInstanceOf(IllegalStateException.class).hasMessageContaining("prod");
	}

	@Test
	void allowsLocalMeasurementProfiles() {
		assertThatCode(() -> new LoadTestProfileGuard(profiles("local", "loadtest"))).doesNotThrowAnyException();
		assertThatCode(() -> new LoadTestProfileGuard(profiles("test", "loadtest"))).doesNotThrowAnyException();
	}

	private static MockEnvironment profiles(String... active) {
		MockEnvironment environment = new MockEnvironment();
		environment.setActiveProfiles(active);
		return environment;
	}
}
