package com.ssafy.c206.backend.integration.leaderboard;

/** 특정 사용자의 순위 조회 결과. rank는 1부터 시작한다. */
public record UserDamageRank(long rank, long totalDamage) {
}
