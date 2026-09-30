package com.ssafy.c206.backend.user.service;

import com.ssafy.c206.backend.user.domain.User;
import com.ssafy.c206.backend.user.domain.UserRole;
import com.ssafy.c206.backend.user.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 기동 시 환경 변수로 지정된 관리자 계정을 1회 생성한다.
 * 이미 존재하는 이메일이면 아무것도 하지 않는다 — 자동 승격은 관리자 이메일을 선점 가입한
 * 제3자를 ADMIN으로 만드는 권한 상승 경로가 되므로 허용하지 않는다(승격은 운영자가 수동으로).
 * 시딩 실패는 로그만 남기고 애플리케이션 기동을 막지 않는다.
 * 계정 정보 미설정 환경(local 등)에서는 아무것도 하지 않는다.
 */
@Component
@Slf4j
public class AdminAccountSeeder implements ApplicationRunner {

	private final UserRepository userRepository;
	private final PasswordEncoder passwordEncoder;
	private final String email;
	private final String password;
	private final String nickname;

	public AdminAccountSeeder(UserRepository userRepository, PasswordEncoder passwordEncoder,
			@Value("${admin.account.email:}") String email,
			@Value("${admin.account.password:}") String password,
			@Value("${admin.account.nickname:admin}") String nickname) {
		this.userRepository = userRepository;
		this.passwordEncoder = passwordEncoder;
		this.email = email;
		this.password = password;
		this.nickname = nickname;
	}

	@Override
	public void run(ApplicationArguments arguments) {
		if (!StringUtils.hasText(email) || !StringUtils.hasText(password)) {
			log.info("Admin account seed skipped: admin.account.email/password are not configured.");
			return;
		}
		try {
			seed();
		} catch (RuntimeException e) {
			log.error("Admin account seed failed; application startup continues without a seeded admin.", e);
		}
	}

	private void seed() {
		User existing = userRepository.findByEmail(User.normalizeEmail(email)).orElse(null);
		if (existing != null) {
			if (existing.getRole() == UserRole.ADMIN) {
				log.info("Admin account seed skipped: admin account already exists.");
			} else {
				log.warn("Admin account seed skipped: user {} already owns the configured email;"
						+ " promote manually if this is intended.", existing.getId());
			}
			return;
		}
		if (userRepository.existsByNickname(nickname.trim())) {
			log.warn("Admin account seed skipped: nickname '{}' is already taken;"
					+ " configure admin.account.nickname with a unique value.", nickname);
			return;
		}
		User admin = userRepository.save(User.registerAdmin(email, passwordEncoder.encode(password), nickname));
		log.info("Admin account seed: created admin user {}.", admin.getId());
	}
}
