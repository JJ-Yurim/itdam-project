package com.ssafy.c206.backend.user;

import static org.assertj.core.api.Assertions.assertThat;

import com.ssafy.c206.backend.user.domain.User;
import org.junit.jupiter.api.Test;

class UserEmailNormalizationTest {

	@Test
	void trimsAndLowercases() {
		assertThat(User.normalizeEmail("  Foo@Example.COM  ")).isEqualTo("foo@example.com");
	}

	@Test
	void stripsPlusTagForGmailDomains() {
		assertThat(User.normalizeEmail("foo+promo@gmail.com")).isEqualTo("foo@gmail.com");
		assertThat(User.normalizeEmail("Foo+a+b@Gmail.com")).isEqualTo("foo@gmail.com");
	}

	@Test
	void unifiesGooglemailDomainToGmail() {
		assertThat(User.normalizeEmail("foo@googlemail.com")).isEqualTo("foo@gmail.com");
		assertThat(User.normalizeEmail("foo+x@googlemail.com")).isEqualTo("foo@gmail.com");
	}

	@Test
	void keepsDotsInLocalPart() {
		// 점 제거는 의도적으로 제외한 정책이다(User.normalizeEmail 주석 참고).
		assertThat(User.normalizeEmail("f.o.o@gmail.com")).isEqualTo("f.o.o@gmail.com");
	}

	@Test
	void keepsPlusTagForOtherDomains() {
		assertThat(User.normalizeEmail("foo+tag@example.com")).isEqualTo("foo+tag@example.com");
	}

	@Test
	void keepsLeadingPlusLocalPart() {
		// 로컬파트가 +로 시작하면 제거 시 빈 로컬파트가 되므로 그대로 둔다.
		assertThat(User.normalizeEmail("+weird@gmail.com")).isEqualTo("+weird@gmail.com");
	}
}
