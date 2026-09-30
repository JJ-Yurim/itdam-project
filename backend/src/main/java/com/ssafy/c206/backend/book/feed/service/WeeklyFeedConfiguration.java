package com.ssafy.c206.backend.book.feed.service;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(WeeklyFeedProperties.class)
public class WeeklyFeedConfiguration {
}
