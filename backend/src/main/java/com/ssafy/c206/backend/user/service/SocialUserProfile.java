package com.ssafy.c206.backend.user.service;

import com.ssafy.c206.backend.user.domain.AuthProvider;

/**
 * provider별 사용자 정보 응답을 공통 형태로 정규화한 프로필.
 * email은 카카오에서 선택 동의라 null일 수 있다.
 */
public record SocialUserProfile(AuthProvider provider, String providerId, String email, String nickname) {
}
