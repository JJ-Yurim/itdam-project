package com.ssafy.c206.backend.user.service;

import com.ssafy.c206.backend.common.api.DomainException;
import com.ssafy.c206.backend.common.api.ErrorCode;
import com.ssafy.c206.backend.user.api.LoginRequest;
import com.ssafy.c206.backend.user.domain.User;
import com.ssafy.c206.backend.user.domain.UserRole;
import com.ssafy.c206.backend.user.domain.UserStatus;
import com.ssafy.c206.backend.user.repository.UserRepository;
import com.ssafy.c206.backend.common.security.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SessionAuthenticationService {

	private final UserRepository userRepository;
	private final PasswordEncoder passwordEncoder;

	@Transactional(readOnly = true)
	public AuthenticationResult authenticate(LoginRequest request) {
		User user = userRepository.findByEmail(User.normalizeEmail(request.email()))
				.orElseThrow(this::unauthorized);
		// 소셜 가입 계정(provider != LOCAL)은 비밀번호 해시가 없으므로 비밀번호 로그인은 항상 실패한다.
		if (user.getStatus() != UserStatus.ACTIVE || user.getPasswordHash() == null
				|| !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
			throw unauthorized();
		}
		return new AuthenticationResult(new AuthenticatedUser(user.getId(), user.getEmail()), user.getRole());
	}

	/** 세션 권한(SecurityContext authorities) 구성을 위해 역할을 함께 반환한다. */
	public record AuthenticationResult(AuthenticatedUser principal, UserRole role) {
	}

	private DomainException unauthorized() {
		return new DomainException(ErrorCode.UNAUTHORIZED, "Invalid email or password.");
	}
}
