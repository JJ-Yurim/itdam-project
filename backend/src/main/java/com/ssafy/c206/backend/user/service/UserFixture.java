package com.ssafy.c206.backend.user.service;

import java.util.Locale;

import com.ssafy.c206.backend.common.api.DomainException;
import com.ssafy.c206.backend.common.api.ErrorCode;
import com.ssafy.c206.backend.user.domain.User;
import com.ssafy.c206.backend.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 테스트·로컬 데이터 구성용 사용자 픽스처. 운영 프로필에는 등록되지 않는다. */
@Service
@Profile({"local", "test", "postgres-test"})
@RequiredArgsConstructor
public class UserFixture {

	private final UserRepository userRepository;

	@Transactional
	public User createFixture(String email, String passwordHash, String nickname) {
		String normalizedEmail = email == null ? null : email.trim().toLowerCase(Locale.ROOT);
		String normalizedNickname = nickname == null ? null : nickname.trim();
		if (userRepository.existsByEmail(normalizedEmail) || userRepository.existsByNickname(normalizedNickname)) {
			throw new DomainException(ErrorCode.CONFLICT, "Email or nickname is already in use.");
		}
		try {
			return userRepository.save(User.createFixture(normalizedEmail, passwordHash, normalizedNickname));
		} catch (DataIntegrityViolationException exception) {
			throw new DomainException(ErrorCode.CONFLICT, "Email or nickname is already in use.");
		}
	}
}
