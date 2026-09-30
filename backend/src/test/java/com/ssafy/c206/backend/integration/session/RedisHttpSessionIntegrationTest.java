package com.ssafy.c206.backend.integration.session;

import static com.ssafy.c206.backend.common.config.SecurityConfiguration.PENDING_SOCIAL_REGISTRATION_SESSION_ATTRIBUTE;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;

import com.ssafy.c206.backend.common.security.AuthenticatedUser;
import com.ssafy.c206.backend.user.domain.AuthProvider;
import com.ssafy.c206.backend.user.service.PendingSocialRegistration;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.session.Session;
import org.springframework.session.SessionRepository;
import org.springframework.session.data.redis.RedisSessionRepository;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Tag("infra")
@Testcontainers
class RedisHttpSessionIntegrationTest {

	@Container
	static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
			.withExposedPorts(6379);

	@Test
	void aSecondBackendRepositoryReadsAuthenticationAndRegistrationStateSavedByTheFirst() {
		LettuceConnectionFactory firstConnectionFactory = connectionFactory();
		LettuceConnectionFactory secondConnectionFactory = connectionFactory();
		SessionRepository<Session> firstRepository = repository(firstConnectionFactory);
		SessionRepository<Session> secondRepository = repository(secondConnectionFactory);

		Session firstSession = firstRepository.createSession();
		AuthenticatedUser authenticatedUser = new AuthenticatedUser(7L, "reader@example.com");
		SecurityContextImpl securityContext = new SecurityContextImpl(
				UsernamePasswordAuthenticationToken.authenticated(authenticatedUser, null, List.of()));
		PendingSocialRegistration pendingRegistration = new PendingSocialRegistration(
				AuthProvider.GOOGLE, "google-user-id", "reader@example.com");

		firstSession.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, securityContext);
		firstSession.setAttribute(PENDING_SOCIAL_REGISTRATION_SESSION_ATTRIBUTE, pendingRegistration);
		firstRepository.save(firstSession);

		try {
			Session secondSession = secondRepository.findById(firstSession.getId());

			assertThat(secondSession).isNotNull();
			SecurityContextImpl loadedSecurityContext = secondSession.getAttribute(
					HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);
			assertThat(loadedSecurityContext.getAuthentication().getPrincipal()).isEqualTo(authenticatedUser);
			assertThat(secondSession.<PendingSocialRegistration>getAttribute(
					PENDING_SOCIAL_REGISTRATION_SESSION_ATTRIBUTE)).isEqualTo(pendingRegistration);
		} finally {
			firstRepository.deleteById(firstSession.getId());
			firstConnectionFactory.destroy();
			secondConnectionFactory.destroy();
		}
	}

	private static LettuceConnectionFactory connectionFactory() {
		LettuceConnectionFactory connectionFactory = new LettuceConnectionFactory(
				new RedisStandaloneConfiguration(REDIS.getHost(), REDIS.getMappedPort(6379)));
		connectionFactory.afterPropertiesSet();
		return connectionFactory;
	}

	@SuppressWarnings("unchecked")
	private static SessionRepository<Session> repository(LettuceConnectionFactory connectionFactory) {
		RedisTemplate<String, Object> redisTemplate = new RedisTemplate<>();
		redisTemplate.setConnectionFactory(connectionFactory);
		redisTemplate.setKeySerializer(new StringRedisSerializer());
		redisTemplate.setHashKeySerializer(new StringRedisSerializer());
		redisTemplate.afterPropertiesSet();

		RedisSessionRepository repository = new RedisSessionRepository(redisTemplate);
		repository.setRedisKeyNamespace("c206:session");
		repository.setDefaultMaxInactiveInterval(Duration.ofMinutes(30));
		return (SessionRepository<Session>) (SessionRepository<?>) repository;
	}
}
