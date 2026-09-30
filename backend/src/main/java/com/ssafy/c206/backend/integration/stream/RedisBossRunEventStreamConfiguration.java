package com.ssafy.c206.backend.integration.stream;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "boss.stream", name = "channel", havingValue = "redis")
class RedisBossRunEventStreamConfiguration {

	@Bean
	RedisBossRunEventStream bossRunEventStream(StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
		return new RedisBossRunEventStream(redisTemplate, objectMapper);
	}

	/** pub/sub 수신은 전용 커넥션이 필요하므로 리스너 컨테이너를 함께 올린다. */
	@Bean
	RedisMessageListenerContainer bossRunEventStreamListenerContainer(RedisConnectionFactory connectionFactory,
			RedisBossRunEventStream eventStream) {
		RedisMessageListenerContainer container = new RedisMessageListenerContainer();
		container.setConnectionFactory(connectionFactory);
		container.addMessageListener(eventStream, new ChannelTopic(RedisBossRunEventStream.CHANNEL));
		container.addMessageListener(eventStream, new ChannelTopic(RedisBossRunEventStream.LEADERBOARD_CHANNEL));
		return container;
	}
}
