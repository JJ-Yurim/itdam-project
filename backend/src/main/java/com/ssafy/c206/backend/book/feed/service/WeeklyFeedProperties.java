package com.ssafy.c206.backend.book.feed.service;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * 주간 선정 문장 피드 정책값. weeklyCount는 세트 발급 시점에만 읽히므로
 * 주중에 값을 바꿔도 이미 발급된 세트는 변하지 않는다.
 */
@ConfigurationProperties(prefix = "app.sentence-feed")
public record WeeklyFeedProperties(
		@DefaultValue("20") int weeklyCount
) {
}
