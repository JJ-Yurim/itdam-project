package com.ssafy.c206.backend.user.service;

import java.time.Duration;
import java.util.Optional;

/**
 * TTL 기반 인증 상태 저장소. 배포 환경은 Redis, 테스트·로컬은 인메모리 구현을 쓴다.
 */
public interface EmailVerificationStore {

	/** 쿨다운을 시작한다. 이미 쿨다운 중이면 false를 반환하고 기존 만료 시각을 유지한다. */
	boolean tryStartCooldown(String email, Duration ttl);

	/** 메일 발송 실패 등으로 선점한 쿨다운을 되돌릴 때 사용한다. */
	void clearCooldown(String email);

	/** 발신 IP의 윈도우 내 발송 횟수를 1 올리고 누적값을 반환한다. 첫 증가 시 윈도우 TTL을 건다. */
	long incrementIpSendCount(String clientIp, Duration window);

	/** 인증 코드를 저장하고 이전 오입력 카운터를 초기화한다. */
	void saveCode(String email, String code, Duration ttl);

	Optional<String> findCode(String email);

	/** 오입력 횟수를 1 올리고 누적값을 반환한다. 첫 증가 시 코드와 같은 TTL을 건다. */
	long incrementFailedAttempts(String email, Duration ttl);

	/** 인증 코드와 오입력 카운터를 함께 제거한다. */
	void removeCode(String email);

	/** 인증 완료 표식을 저장한다. 값은 confirm 응답으로 내려간 1회용 검증 토큰이다. */
	void markVerified(String email, String token, Duration ttl);

	/** 저장된 검증 토큰을 반환한다. 표식이 없거나 만료면 empty. 가입 시 요청 토큰과 대조한다. */
	Optional<String> findVerifiedToken(String email);

	/** 가입 성공 시 인증 표식을 소모해 1회용으로 만든다. */
	void consumeVerified(String email);
}
