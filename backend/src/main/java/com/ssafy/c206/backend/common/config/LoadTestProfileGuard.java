package com.ssafy.c206.backend.common.config;

import java.util.Set;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * loadtest 프로필이 배포 프로필과 함께 켜지는 사고를 기동 시점에 막는다.
 *
 * <p>application-loadtest.properties는 src/main/resources에 있어 배포 jar에 그대로 실린다.
 * 프로필을 켜지 않으면 읽히지 않지만, SPRING_PROFILES_ACTIVE에 loadtest가 실수로 섞이면
 * 일일 제출 한도가 사실상 무제한이 되고 AI 클라이언트가 Fake로 바뀌는 일이 아무 경고 없이
 * 동시에 일어난다. 한도 무력화는 조용히 진행되므로 테스트로는 잡히지 않고, 여기서 기동을
 * 실패시키는 것이 가장 이른 방어선이다.
 */
@Slf4j
@Component
@Profile("loadtest")
public class LoadTestProfileGuard {

	private static final Set<String> DEPLOYMENT_PROFILES = Set.of("develop", "prod");

	public LoadTestProfileGuard(Environment environment) {
		for (String profile : environment.getActiveProfiles()) {
			if (DEPLOYMENT_PROFILES.contains(profile)) {
				throw new IllegalStateException(
						"The loadtest profile must not be combined with the '" + profile + "' deployment profile. "
								+ "It removes the daily submission limit and swaps AI clients with fakes.");
			}
		}
		// 로컬 측정에서도 이 프로필이 켜져 있음을 기동 로그 한 줄로 드러낸다.
		log.warn("loadtest_profile_active daily submission limit is raised and external AI clients are faked");
	}
}
