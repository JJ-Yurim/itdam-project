package com.ssafy.c206.backend.integration.leaderboard;

/** 리더보드 한 항목: 사용자와 그 회차 누적 데미지. */
public record UserDamageTotal(long userId, long totalDamage) {
}
