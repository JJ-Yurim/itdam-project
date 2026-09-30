package com.ssafy.c206.backend.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpSession;
import jakarta.servlet.http.Cookie;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.test.util.ReflectionTestUtils;
import com.ssafy.c206.backend.user.api.SignupRequest;
import com.ssafy.c206.backend.user.service.EmailVerificationStore;
import com.ssafy.c206.backend.user.service.RegistrationService;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
@ActiveProfiles("test")
class SecurityIntegrationTest {

	private static final AtomicInteger SEQUENCE = new AtomicInteger();

	@Autowired private WebApplicationContext webApplicationContext;
	@Autowired private FilterChainProxy springSecurityFilterChain;
	@Autowired private ObjectMapper objectMapper;
	@Autowired private CsrfTokenRepository csrfTokenRepository;
	@Autowired private RegistrationService registrationService;
	@Autowired private EmailVerificationStore emailVerificationStore;

	private MockMvc mockMvc;

	@Test
	void csrfRepositoryUsesSpaHeaderName() throws Exception {
		var request = new MockHttpServletRequest();
		assertThat(csrfTokenRepository.generateToken(request).getHeaderName()).isEqualTo("X-XSRF-TOKEN");
	}

	@BeforeEach
	void setUp() {
		springSecurityFilterChain.getFilters("/api/v1/csrf").stream()
				.filter(CsrfFilter.class::isInstance)
				.findFirst()
				.ifPresent(filter -> ReflectionTestUtils.setField(filter, "tokenRepository", csrfTokenRepository));
		mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
				.addFilters(springSecurityFilterChain).build();
	}

	@Test
	void anonymousSpaCanObtainCsrfTokenCookieAndBody() throws Exception {
		MvcResult result = mockMvc.perform(get("/api/v1/csrf").session(new MockHttpSession()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.token").isString())
				.andReturn();

		String token = objectMapper.readTree(result.getResponse().getContentAsString()).path("token").asText();
		assertThat(result.getResponse().getHeaders("Set-Cookie")).hasSize(1);
		String cookie = result.getResponse().getHeaders("Set-Cookie").getFirst();
		assertThat(cookie).startsWith("XSRF-TOKEN=").doesNotContain("HttpOnly");
		assertThat(cookie.substring("XSRF-TOKEN=".length(), cookie.indexOf(';'))).isNotBlank();
		assertThat(token).isNotBlank();
	}

	@Test
	void csrfIsRequiredForLoginAndValidTokenAllowsLogin() throws Exception {
		String email = registerUser("csrf-login");
		String body = "{\"email\":\"" + email + "\",\"password\":\"password123\"}";

		mockMvc.perform(post("/api/v1/auth/sessions").contentType("application/json").content(body))
				.andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("FORBIDDEN"));
		mockMvc.perform(post("/api/v1/auth/sessions").with(csrf()).contentType("application/json").content(body))
				.andExpect(status().isOk());
	}

	@Test
	void rawCookieAndHeaderFromCsrfEndpointAllowLogin() throws Exception {
		MockHttpSession session = new MockHttpSession();
		MvcResult csrf = mockMvc.perform(get("/api/v1/csrf").session(session)).andExpect(status().isOk()).andReturn();
		String token = objectMapper.readTree(csrf.getResponse().getContentAsString()).path("token").asText();
		String cookie = csrf.getResponse().getHeaders("Set-Cookie").getFirst();
		String cookieToken = cookie.substring("XSRF-TOKEN=".length(), cookie.indexOf(';'));
		String email = "csrf-browser" + SEQUENCE.incrementAndGet() + "@security.test";
		String verificationToken = EmailVerificationTestSupport.markVerified(emailVerificationStore, email);
		registrationService.register(new SignupRequest(
				email, "password123", "csrf-browser" + SEQUENCE.incrementAndGet(), verificationToken,
				EmailVerificationTestSupport.CURRENT_COPYRIGHT_AGREEMENT_ID, true));
		mockMvc.perform(post("/api/v1/auth/sessions").session(session)
				.cookie(new Cookie("XSRF-TOKEN", cookieToken))
				.header("X-XSRF-TOKEN", cookieToken).contentType("application/json")
				.content("{\"email\":\"" + email + "\",\"password\":\"password123\"}"))
				.andExpect(status().isOk());
	}

	@Test
	void loginRotatesCsrfTokenSoSpaCanFetchANewToken() throws Exception {
		MvcResult anonymous = mockMvc.perform(get("/api/v1/csrf")).andExpect(status().isOk()).andReturn();
		String anonymousToken = objectMapper.readTree(anonymous.getResponse().getContentAsString())
				.path("token").asText();
		String email = registerUser("csrf-rotate");

		MvcResult login = mockMvc.perform(post("/api/v1/auth/sessions").with(csrf()).contentType("application/json")
				.content("{\"email\":\"" + email + "\",\"password\":\"password123\"}"))
				.andExpect(status().isOk()).andReturn();
		assertThat(login.getResponse().getHeaders("Set-Cookie")).anySatisfy(cookie ->
				assertThat(cookie).startsWith("XSRF-TOKEN=").doesNotContain("Max-Age=0"));

		MvcResult authenticated = mockMvc.perform(get("/api/v1/csrf")
				.session((MockHttpSession) login.getRequest().getSession(false)))
				.andExpect(status().isOk()).andReturn();
		String authenticatedToken = objectMapper.readTree(authenticated.getResponse().getContentAsString())
				.path("token").asText();
		assertThat(authenticatedToken).isNotEqualTo(anonymousToken);
	}

	@Test
	void unsafeAuthenticatedRequestRejectsMissingAndInvalidCsrf() throws Exception {
		MockHttpSession session = registerAndLogin("csrf-unsafe");
		mockMvc.perform(post("/api/v1/sentences").session(session).contentType("application/json")
				.content("{\"content\":\"unsafe request\"}"))
				.andExpect(status().isForbidden());
		mockMvc.perform(post("/api/v1/sentences").session(session).header("X-XSRF-TOKEN", "invalid")
				.contentType("application/json").content("{\"content\":\"unsafe request\"}"))
				.andExpect(status().isForbidden());
	}

	@Test
	void safeGetDoesNotRequireCsrfButUnauthenticatedApiReturnsJson401() throws Exception {
		mockMvc.perform(get("/api/v1/users/me"))
				.andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
	}

	@Test
	void logoutRequiresCsrfAndInvalidatesSession() throws Exception {
		MockHttpSession session = registerAndLogin("csrf-logout");
		mockMvc.perform(post("/api/v1/auth/sessions/current").session(session))
				.andExpect(status().isForbidden());
		mockMvc.perform(post("/api/v1/auth/sessions/current").with(csrf()).session(session))
				.andExpect(status().isNoContent());
		mockMvc.perform(get("/api/v1/users/me").session(session))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void loginChangesSessionId() throws Exception {
		String email = registerUser("fixation");
		MockHttpSession session = new MockHttpSession();
		String before = session.getId();
		MvcResult result = mockMvc.perform(post("/api/v1/auth/sessions").with(csrf()).session(session)
				.contentType("application/json")
				.content("{\"email\":\"" + email + "\",\"password\":\"password123\"}"))
				.andExpect(status().isOk()).andReturn();
		assertThat(result.getRequest().getSession(false).getId()).isNotEqualTo(before);
	}

	@Test
	void localCorsAllowsConfiguredViteOriginWithCredentials() throws Exception {
		mockMvc.perform(options("/api/v1/csrf").header("Origin", "http://localhost:5173")
				.header("Access-Control-Request-Method", "POST"))
				.andExpect(status().isOk())
				.andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"))
				.andExpect(header().string("Access-Control-Allow-Credentials", "true"));
	}

	@Test
	void nonAllowedCorsOriginDoesNotReceiveCorsGrant() throws Exception {
		mockMvc.perform(options("/api/v1/csrf").header("Origin", "http://untrusted.example")
				.header("Access-Control-Request-Method", "POST"))
				.andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
	}

	// 프로필별 쿠키·연결 정책 검증은 설정 파일 문자열 비교(브리틀) 대신
	// 실제 프로필 기동 검증(DevelopInfrastructureIntegrationTest 등)으로 수행한다.

	private String registerUser(String prefix) throws Exception {
		String email = prefix + SEQUENCE.incrementAndGet() + "@security.test";
		mockMvc.perform(post("/api/v1/users").with(csrf()).contentType("application/json")
				.content(EmailVerificationTestSupport.verifiedSignupBody(emailVerificationStore, email,
						"password123", prefix + SEQUENCE.incrementAndGet())))
				.andExpect(status().isCreated());
		return email;
	}

	private MockHttpSession registerAndLogin(String prefix) throws Exception {
		String email = registerUser(prefix);
		MvcResult login = mockMvc.perform(post("/api/v1/auth/sessions").with(csrf())
				.contentType("application/json")
				.content("{\"email\":\"" + email + "\",\"password\":\"password123\"}"))
				.andExpect(status().isOk()).andReturn();
		return (MockHttpSession) login.getRequest().getSession(false);
	}
}
