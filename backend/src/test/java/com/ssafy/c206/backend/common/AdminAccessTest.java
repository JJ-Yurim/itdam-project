package com.ssafy.c206.backend.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ssafy.c206.backend.user.domain.User;
import com.ssafy.c206.backend.user.domain.UserRole;
import com.ssafy.c206.backend.user.repository.UserRepository;
import com.ssafy.c206.backend.user.EmailVerificationTestSupport;
import com.ssafy.c206.backend.user.service.AdminAccountSeeder;
import com.ssafy.c206.backend.user.service.EmailVerificationStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/** /api/admin 경로가 ADMIN 역할 세션에만 열리는지, 시딩이 안전하게 동작하는지 검증한다. (대표 경로: 보스 원형 목록) */
@SpringBootTest
@ActiveProfiles("test")
class AdminAccessTest {

	// 공유 H2 규약: 테스트 데이터 식별자는 실행마다 고유값을 쓴다.
	private static final String SUFFIX = String.valueOf(System.nanoTime());
	private static final String ADMIN_EMAIL = "seeded-admin-" + SUFFIX + "@access.test";
	private static final String ADMIN_PASSWORD = "admin-password-123";
	private static final String ADMIN_NICKNAME = "seeded-admin-" + SUFFIX;

	@Autowired private WebApplicationContext webApplicationContext;
	@Autowired private FilterChainProxy springSecurityFilterChain;
	@Autowired private UserRepository userRepository;
	@Autowired private EmailVerificationStore emailVerificationStore;
	@Autowired private PasswordEncoder passwordEncoder;

	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
				.addFilters(springSecurityFilterChain).build();
		seeder(ADMIN_EMAIL, ADMIN_PASSWORD, ADMIN_NICKNAME).run(null);
	}

	@Test
	void anonymousRequestIsUnauthorized() throws Exception {
		mockMvc.perform(get("/api/admin/bosses"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
	}

	@Test
	void normalUserIsForbidden() throws Exception {
		String suffix = String.valueOf(System.nanoTime());
		String email = "normal-" + suffix + "@access.test";
		register(email, "password123", "n" + suffix);
		MockHttpSession session = login(email, "password123");

		mockMvc.perform(get("/api/admin/bosses").session(session))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("FORBIDDEN"));
	}

	@Test
	void seededAdminCanAccessAdminApiAndLoginExposesRole() throws Exception {
		MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/sessions").with(csrf())
					.contentType("application/json")
					.content("{\"email\":\"" + ADMIN_EMAIL + "\",\"password\":\"" + ADMIN_PASSWORD + "\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.role").value("ADMIN"))
				.andReturn();
		MockHttpSession session = (MockHttpSession) loginResult.getRequest().getSession(false);

		mockMvc.perform(get("/api/admin/bosses").session(session))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content").isArray());
	}

	@Test
	void existingUserAccountIsNotAutoPromoted() throws Exception {
		String suffix = String.valueOf(System.nanoTime());
		String email = "occupied-" + suffix + "@access.test";
		register(email, "original-pass-123", "occ" + suffix);

		seeder(email, "attacker-pass-123", "occ-admin-" + suffix).run(null);

		User user = userRepository.findByEmail(email).orElseThrow();
		assertThat(user.getRole()).isEqualTo(UserRole.USER);
		// 비밀번호도 덮이지 않아 기존 비밀번호로만 로그인되고, 권한은 여전히 일반 사용자다.
		MockHttpSession session = login(email, "original-pass-123");
		mockMvc.perform(get("/api/admin/bosses").session(session))
				.andExpect(status().isForbidden());
	}

	@Test
	void nicknameConflictSkipsSeedingWithoutFailing() throws Exception {
		String suffix = String.valueOf(System.nanoTime());
		String takenNickname = "taken" + suffix;
		register("holder-" + suffix + "@access.test", "password123", takenNickname);
		String freshEmail = "fresh-" + suffix + "@access.test";

		seeder(freshEmail, "whatever-123", takenNickname).run(null);

		assertThat(userRepository.findByEmail(freshEmail)).isEmpty();
	}

	private AdminAccountSeeder seeder(String email, String password, String nickname) {
		// 시더는 자체 트랜잭션(repository)만 사용하므로 직접 생성해도 프로덕션과 동일하게 동작한다.
		return new AdminAccountSeeder(userRepository, passwordEncoder, email, password, nickname);
	}

	private void register(String email, String password, String nickname) throws Exception {
		mockMvc.perform(post("/api/v1/users").with(csrf()).contentType("application/json")
					.content(EmailVerificationTestSupport.verifiedSignupBody(
							emailVerificationStore, email, password, nickname)))
				.andExpect(status().isCreated());
	}

	private MockHttpSession login(String email, String password) throws Exception {
		MvcResult login = mockMvc.perform(post("/api/v1/auth/sessions").with(csrf())
					.contentType("application/json")
					.content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"))
				.andExpect(status().isOk()).andReturn();
		MockHttpSession session = (MockHttpSession) login.getRequest().getSession(false);
		assertThat(session).as("login must create a session").isNotNull();
		return session;
	}
}
