package com.ssafy.c206.backend.infra;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.UUID;

import com.ssafy.c206.backend.integration.ai.AiEvaluationClient;
import com.ssafy.c206.backend.integration.ai.HttpAiEvaluationClient;
import com.ssafy.c206.backend.integration.mail.SmtpVerificationMailSender;
import com.ssafy.c206.backend.integration.mail.VerificationMailSender;
import com.ssafy.c206.backend.user.service.EmailVerificationStore;
import com.ssafy.c206.backend.user.service.RedisEmailVerificationStore;
import net.javacrumbs.shedlock.core.LockProvider;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.health.actuate.endpoint.HealthEndpoint;
import org.springframework.boot.health.contributor.Status;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.session.Session;
import org.springframework.session.SessionRepository;
import org.springframework.session.data.redis.RedisSessionRepository;
import org.springframework.session.web.http.CookieSerializer;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Tag("infra")
@SpringBootTest
@ActiveProfiles("develop")
@Testcontainers
class DevelopInfrastructureIntegrationTest {

	private static final String REDIS_PASSWORD = "test-redis-password";
	private static final String RABBITMQ_USERNAME = "test-user";
	private static final String RABBITMQ_PASSWORD = "test-password";

	// develop 프로필은 Flyway를 실행하므로 pgvector가 포함된 이미지여야 한다(V4).
	@Container
	static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
			DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"));

	@Container
	static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
			.withExposedPorts(6379)
			.withCommand("redis-server", "--requirepass", REDIS_PASSWORD);

	@Container
	static final RabbitMQContainer RABBITMQ =
			new RabbitMQContainer(DockerImageName.parse("rabbitmq:3.13-management-alpine"))
					.withAdminUser(RABBITMQ_USERNAME)
					.withAdminPassword(RABBITMQ_PASSWORD);

	@DynamicPropertySource
	static void configureInfrastructure(DynamicPropertyRegistry registry) {
		registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
		registry.add("spring.datasource.username", POSTGRES::getUsername);
		registry.add("spring.datasource.password", POSTGRES::getPassword);
		registry.add("spring.data.redis.host", REDIS::getHost);
		registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
		registry.add("spring.data.redis.password", () -> REDIS_PASSWORD);
		registry.add("spring.rabbitmq.host", RABBITMQ::getHost);
		registry.add("spring.rabbitmq.port", RABBITMQ::getAmqpPort);
		registry.add("spring.rabbitmq.username", () -> RABBITMQ_USERNAME);
		registry.add("spring.rabbitmq.password", () -> RABBITMQ_PASSWORD);
		registry.add("spring.rabbitmq.virtual-host", () -> "/");
		registry.add("spring.flyway.enabled", () -> true);
		// develop 프로필은 email.verification.mail=smtp라 발신자(username)가 없으면 기동에 실패한다.
		registry.add("spring.mail.username", () -> "infra-test@example.com");
		registry.add("spring.mail.password", () -> "infra-test-password");
		// develop 프로필의 소셜 로그인 fail-fast 선언(자격증명·리다이렉트 베이스 필수)을 더미로 충족시킨다.
		registry.add("spring.security.oauth2.client.registration.kakao.client-id", () -> "infra-test-kakao");
		registry.add("spring.security.oauth2.client.registration.naver.client-id", () -> "infra-test-naver");
		registry.add("spring.security.oauth2.client.registration.google.client-id", () -> "infra-test-google");
		registry.add("spring.security.oauth2.client.registration.kakao.redirect-uri",
				() -> "http://localhost:8080/api/login/oauth2/code/kakao");
		registry.add("spring.security.oauth2.client.registration.naver.redirect-uri",
				() -> "http://localhost:8080/api/login/oauth2/code/naver");
		registry.add("spring.security.oauth2.client.registration.google.redirect-uri",
				() -> "http://localhost:8080/api/login/oauth2/code/google");
	}

	@Autowired
	private HealthEndpoint healthEndpoint;
	@Autowired
	private AiEvaluationClient aiEvaluationClient;
	@Autowired
	private EmailVerificationStore emailVerificationStore;
	@Autowired
	private VerificationMailSender verificationMailSender;
	@Autowired
	private ApplicationContext applicationContext;
	@Autowired
	private StringRedisTemplate redisTemplate;

	@Test
	void reportsUpWhenDevelopInfrastructureConnectionsAreHealthy() {
		assertThat(healthEndpoint.health().getStatus()).isEqualTo(Status.UP);
		assertThat(aiEvaluationClient).isInstanceOf(HttpAiEvaluationClient.class);
	}

	// develop 프로필은 이메일 인증 상태를 Redis에 저장하고 실제 SMTP로 발송해야 한다.
	@Test
	void emailVerificationUsesRedisStoreAndSmtpSender() {
		assertThat(emailVerificationStore).isInstanceOf(RedisEmailVerificationStore.class);
		assertThat(verificationMailSender).isInstanceOf(SmtpVerificationMailSender.class);

		// 컨테이너 재사용·테스트 재실행에도 안전하도록 실행마다 고유 키를 쓰고 끝나면 정리한다.
		String email = "infra-roundtrip-" + UUID.randomUUID() + "@example.com";
		try {
			emailVerificationStore.saveCode(email, "123456", Duration.ofMinutes(5));
			assertThat(emailVerificationStore.findCode(email)).contains("123456");

			assertThat(emailVerificationStore.tryStartCooldown(email, Duration.ofSeconds(60))).isTrue();
			assertThat(emailVerificationStore.tryStartCooldown(email, Duration.ofSeconds(60))).isFalse();

			assertThat(emailVerificationStore.incrementFailedAttempts(email, Duration.ofMinutes(5))).isEqualTo(1);
			assertThat(emailVerificationStore.incrementFailedAttempts(email, Duration.ofMinutes(5))).isEqualTo(2);

			emailVerificationStore.removeCode(email);
			assertThat(emailVerificationStore.findCode(email)).isEmpty();

			emailVerificationStore.markVerified(email, "roundtrip-token", Duration.ofMinutes(10));
			assertThat(emailVerificationStore.findVerifiedToken(email)).contains("roundtrip-token");

			emailVerificationStore.consumeVerified(email);
			assertThat(emailVerificationStore.findVerifiedToken(email)).isEmpty();
		} finally {
			emailVerificationStore.removeCode(email);
			emailVerificationStore.clearCooldown(email);
		}
	}

	@Test
	void deployedProfileStoresHttpSessionsInTheSharedRedisNamespace() {
		assertThat(applicationContext.getBeansOfType(SessionRepository.class).values())
				.singleElement()
				.isInstanceOf(RedisSessionRepository.class);

		@SuppressWarnings("unchecked")
		SessionRepository<Session> repository =
				(SessionRepository<Session>) (SessionRepository<?>) applicationContext.getBean(SessionRepository.class);
		Session session = repository.createSession();
		repository.save(session);
		try {
			assertThat(session.getMaxInactiveInterval()).isEqualTo(Duration.ofMinutes(30));
			assertThat(redisTemplate.hasKey("c206:session:sessions:" + session.getId())).isTrue();
		} finally {
			repository.deleteById(session.getId());
		}
	}

	@Test
	void deployedProfileWritesTheSharedSecureSessionCookie() {
		CookieSerializer serializer = applicationContext.getBean(CookieSerializer.class);
		MockHttpServletRequest request = new MockHttpServletRequest();
		MockHttpServletResponse response = new MockHttpServletResponse();

		serializer.writeCookieValue(new CookieSerializer.CookieValue(request, response, "session-id"));

		assertThat(response.getHeader(HttpHeaders.SET_COOKIE))
				.startsWith("JSESSIONID=")
				.contains("Path=/", "HttpOnly", "Secure", "SameSite=Lax");
	}

	@Test
	void deployedProfileCreatesTheRedisSchedulerLockProvider() {
		assertThat(applicationContext.getBeansOfType(LockProvider.class).values()).singleElement();
	}
}
