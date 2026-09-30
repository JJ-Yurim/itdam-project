package com.ssafy.c206.backend.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;

import com.ssafy.c206.backend.common.api.DomainException;
import com.ssafy.c206.backend.user.domain.AuthProvider;
import com.ssafy.c206.backend.user.domain.User;
import com.ssafy.c206.backend.user.domain.UserRole;
import com.ssafy.c206.backend.user.domain.UserStatus;
import com.ssafy.c206.backend.user.repository.UserRepository;
import com.ssafy.c206.backend.user.service.UserFixture;
import com.ssafy.c206.backend.user.service.UserQueryService;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class UserPersistenceTest {

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private UserQueryService userQueryService;
	@Autowired
	private UserFixture userFixture;

	@Autowired
	private Validator validator;

	@BeforeEach
	void cleanUsers() {
		userRepository.deleteAll();
	}

	@Test
	void normalizesEmailAndNicknameAndSetsTimestamps() {
		User user = userFixture.createFixture(" Alice@Example.COM ", "hash", "  Alice  ");

		assertThat(user.getEmail()).isEqualTo("alice@example.com");
		assertThat(user.getNickname()).isEqualTo("Alice");
		assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);
		assertThat(user.getCreatedAt()).isNotNull();
		assertThat(user.getUpdatedAt()).isNotNull();
		assertThat(user.getCreatedAt()).isBeforeOrEqualTo(Instant.now());
	}

	@Test
	void rejectsDuplicateNormalizedEmailAndNickname() {
		userFixture.createFixture("alice@example.com", "hash", "Alice");

		assertThatThrownBy(() -> userFixture.createFixture(" ALICE@example.com ", "hash2", "Other"))
				.isInstanceOf(DomainException.class)
				.extracting("errorCode").isEqualTo(com.ssafy.c206.backend.common.api.ErrorCode.CONFLICT);
		assertThatThrownBy(() -> userFixture.createFixture("other@example.com", "hash2", " Alice "))
				.isInstanceOf(DomainException.class);
	}

	@Test
	void validatesFieldsAndDefaultsToUserRole() {
		User invalid = User.createFixture("not-an-email", " ", "");
		assertThat(validator.validate(invalid)).extracting(ConstraintViolation::getPropertyPath)
				.anyMatch(path -> path.toString().equals("email"));
		assertThat(validator.validate(invalid)).extracting(ConstraintViolation::getPropertyPath)
				.anyMatch(path -> path.toString().equals("nickname"));
		assertThat(User.createFixture("role-default@example.com", "hash", "role-default").getRole())
				.isEqualTo(UserRole.USER);
	}

	@Test
	void socialUserWithoutPasswordIsValid() {
		// 소셜 계정은 비밀번호 해시가 없어도 유효하다(ISSUE-120에서 @NotBlank 완화).
		User social = User.registerSocial(AuthProvider.KAKAO, "kakao-1", "social-valid@example.com", "소셜검증");
		assertThat(validator.validate(social)).isEmpty();
		assertThat(social.getPasswordHash()).isNull();
	}

	@Test
	void rejectsInactiveUserAndReturnsActiveUser() {
		User inactive = userRepository.save(User.createFixture("inactive@example.com", "hash", "Inactive", UserStatus.INACTIVE));
		User active = userFixture.createFixture("active@example.com", "hash", "Active");

		assertThat(userQueryService.getActiveUser(active.getId()).getId()).isEqualTo(active.getId());
		assertThatThrownBy(() -> userQueryService.getActiveUser(inactive.getId()))
				.isInstanceOf(DomainException.class)
				.extracting("errorCode").isEqualTo(com.ssafy.c206.backend.common.api.ErrorCode.FORBIDDEN);
	}
}
